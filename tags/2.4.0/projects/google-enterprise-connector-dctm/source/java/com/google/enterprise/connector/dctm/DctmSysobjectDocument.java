// Copyright (C) 2006-2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.dctm;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.enterprise.connector.dctm.dfcwrap.IAttr;
import com.google.enterprise.connector.dctm.dfcwrap.IFormat;
import com.google.enterprise.connector.dctm.dfcwrap.IId;
import com.google.enterprise.connector.dctm.dfcwrap.ISession;
import com.google.enterprise.connector.dctm.dfcwrap.ISysObject;
import com.google.enterprise.connector.dctm.dfcwrap.ITime;
import com.google.enterprise.connector.dctm.dfcwrap.IType;
import com.google.enterprise.connector.dctm.dfcwrap.IValue;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RepositoryLoginException;
import com.google.enterprise.connector.spi.SkippedDocumentException;
import com.google.enterprise.connector.spi.SimpleProperty;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.SpiConstants.ActionType;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.Value;

public class DctmSysobjectDocument implements Document {
  private static final Logger logger =
      Logger.getLogger(DctmSysobjectDocument.class.getName());

  /** The maximum content size that will be allowed. */
  private static final long MAX_CONTENT_SIZE = 30L * 1024 * 1024;
  private static final String OBJECT_ID_NAME = "r_object_id";

  private final DctmTraversalManager traversalManager;
  private final ISession session;
  private final String docId;
  private String versionId;
  private ITime timeStamp;
  private final ActionType action;
  private final Checkpoint checkpoint;

  private ISysObject object;

  public DctmSysobjectDocument(DctmTraversalManager traversalManager,
      ISession session, String docid, String commonVersionID, ITime timeStamp,
      ActionType action, Checkpoint checkpoint) {
    this.traversalManager = traversalManager;
    this.session = session;
    this.docId = docid;
    this.versionId = commonVersionID;
    this.timeStamp = timeStamp;
    this.action = action;
    this.checkpoint = checkpoint;
  }

  private void fetch() throws RepositoryDocumentException,
      RepositoryLoginException, RepositoryException {
    if (object != null || !ActionType.ADD.equals(action)) {
      return;
    }
    try {
      IId id = traversalManager.getClientX().getId(docId);

      object = session.getObject(id);
      if (versionId == null || versionId.length() == 0) {
        versionId = object.getId("i_chronicle_id").getId();
      }
      logger.fine("i_chronicle_id of the fetched object is " + versionId);

      if (timeStamp == null) {
        timeStamp = object.getTime("r_modify_date");
      }
    } catch (RepositoryDocumentException rde) {
      // Propagate unmolested.
      throw rde;
    } catch (RepositoryException re) {
      if (checkpoint != null && lostConnection(session)) {
        // We have lost connectivity with the server.
        // Rollback the checkpoint and retry this document later.
        checkpoint.restore();
      }
      throw re;
    }
  }

  /**
   * Test connectivity to server.  If we have a session and
   * can verify that session isConnected(), return false.
   * If we cannot verify the session is connected return true.
   */
  private boolean lostConnection(ISession session) {
    try {
      return !session.isConnected();
    } catch (Exception e) {
      logger.warning("Lost connectivity to server : " + e);
      return true;
    }
  }

  public Property findProperty(String name) throws RepositoryDocumentException,
      RepositoryLoginException, RepositoryException {
    LinkedList<Value> values = new LinkedList<Value>();

    logger.fine("In findProperty; name: " + name);

    if (ActionType.ADD.equals(action)) {
      fetch();
      if (SpiConstants.PROPNAME_ACTION.equals(name)) {
        values.add(Value.getStringValue(action.toString()));
      } else if (name.equals(SpiConstants.PROPNAME_DOCID)) {
        values.add(Value.getStringValue(versionId));
      } else if (SpiConstants.PROPNAME_CONTENT.equals(name)) {
        try {
          if (canIndex(true)) {
            values.add(Value.getBinaryValue(object.getContent()));
          }
        } catch (RepositoryDocumentException e) {
          // FIXME: In the unlikely event the user only has BROWSE
          // permission for a document, we'll end up here. That's
          // fine, but the google:mimetype property will not be reset
          // to "text/plain" in that case.
          logger.warning("RepositoryDocumentException thrown: " + e
                         + " on getting property : " + name);
        }
      } else if (SpiConstants.PROPNAME_DISPLAYURL.equals(name)) {
        String displayUrl = traversalManager.getServerUrl() + docId;
        values.add(Value.getStringValue(displayUrl));
      } else if (SpiConstants.PROPNAME_SECURITYTOKEN.equals(name)) {
        try {
          values.add(Value.getStringValue(object.getACLDomain() + " "
                                          + object.getACLName()));
        } catch (RepositoryDocumentException e) {
          // TODO Auto-generated catch block
          logger.warning("RepositoryDocumentException thrown : " + e
                         + " on getting property : " + name);
        }
      } else if (SpiConstants.PROPNAME_ISPUBLIC.equals(name)) {
        values.add(Value.getBooleanValue(traversalManager.isPublic()));
      } else if (SpiConstants.PROPNAME_LASTMODIFIED.equals(name)) {
        if (timeStamp != null) {
          values.add(Value.getDateValue(getCalendarFromDate(timeStamp.getDate())));
        }
      } else if (SpiConstants.PROPNAME_MIMETYPE.equals(name)) {
        try {
          IFormat dctmForm = object.getFormat();
          String mimetype = dctmForm.getMIMEType();
          logger.fine("mimetype of the document " + versionId + " : " + mimetype);
          // Modification in order to index empty documents.
          if (canIndex(false)) {
            values.add(Value.getStringValue(mimetype));
          }
        } catch (RepositoryDocumentException e) {
          // TODO Auto-generated catch block
          logger.warning("RepositoryDocumentException thrown : " + e
                         + " on getting property : " + name);
        }
      } else if (OBJECT_ID_NAME.equals(name)) {
        values.add(Value.getStringValue(docId));
      } else if (SpiConstants.PROPNAME_TITLE.equals(name)) {
        values.add(Value.getStringValue(object.getObjectName()));
      } else if (name.equals("r_object_type")) {
        // Retrieves object type and its super type(s).
        for (IType value = object.getType(); value != null; value = getSuperType(value)) {
          String typeName = value.getName();
          values.add(Value.getStringValue(typeName));
        }
      } else {
        // TODO: We could store the data types for each attribute in
        // the type attributes cache, and save about 2% of the
        // traversal time here by avoiding the calls to findAttrIndex
        // and getAttr.
        int attrIndex = object.findAttrIndex(name);
        if (attrIndex != -1) {
          IAttr attr = object.getAttr(attrIndex);
          for (int i = 0, n = object.getValueCount(name); i < n; i++) {
            IValue val = object.getRepeatingValue(name, i);
            try {
              switch (attr.getDataType()) {
                case IAttr.DM_BOOLEAN:
                  values.add(Value.getBooleanValue(val.asBoolean()));
                  break;
                case IAttr.DM_DOUBLE:
                  values.add(Value.getDoubleValue(val.asDouble()));
                  break;
                case IAttr.DM_ID:
                  // TODO: Should we check for null here?
                  values.add(Value.getStringValue(val.asId().getId()));
                  break;
                case IAttr.DM_INTEGER:
                  values.add(Value.getLongValue(val.asInteger()));
                  break;
                case IAttr.DM_STRING:
                  values.add(Value.getStringValue(val.asString()));
                  break;
                case IAttr.DM_TIME:
                  Date date = val.asTime().getDate();
                  if (date != null) {
                    values.add(Value.getDateValue(getCalendarFromDate(date)));
                  }
                  break;
                default:
                  // TODO: Should this be an exception, or just logged
                  // directly as a warning?
                  throw new AssertionError(String.valueOf(attr.getDataType()));
              }
            } catch (Exception e) {
              logger.warning("error getting the value of index "
                  + i +" of the attribute " + name);
              logger.warning("exception " + e);
            }
          }
        } else {
          // No property by that name found.
          return null;
        }
      }
    } else {
      if (SpiConstants.PROPNAME_ACTION.equals(name)) {
        values.add(Value.getStringValue(action.toString()));
      } else if (name.equals(SpiConstants.PROPNAME_DOCID)) {
        values.add(Value.getStringValue(versionId));
      } else if (name.equals(SpiConstants.PROPNAME_LASTMODIFIED)) {
        if (timeStamp != null) {
          values.add(Value.getDateValue(getCalendarFromDate(timeStamp.getDate())));
        }
      } else if (OBJECT_ID_NAME.equals(name)) {
        values.add(Value.getStringValue(docId));
      } else {
        // No property by that name found.
        return null;
      }
    }

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("property " + name + " has the values " + values);
    }
    return new SimpleProperty(values);
  }

  /**
   * Return the supertype for the supplied type.  Caches result to
   * avoid frequent round-trips to server.
   *
   * @return superType for supplied type, or null if type is root type.
   */
  private IType getSuperType(IType type) throws RepositoryException {
    if (type == null)
      return null;
    Map<String, IType> superTypes = traversalManager.getSuperTypeCache();
    String typeName = type.getName();
    if (superTypes.containsKey(typeName))
      return superTypes.get(typeName);
    IType superType = type.getSuperType();
    superTypes.put(typeName, superType);
    return superType;
  }

  /**
   * Return true if the obect's content should be supplied for indexing.
   */
  private boolean canIndex(boolean logging) throws RepositoryException {
    // Don't send content that is too big or too small.
    long contentSize = object.getContentSize();
    TraversalContext traversalContext = traversalManager.getTraversalContext();

    // Don't send content whose mimetype is not supported.
    IFormat format = object.getFormat();
    String mimetype = format.getMIMEType();
    if (traversalContext != null) {
      int supportLevel = traversalContext.mimeTypeSupportLevel(mimetype);
      if (supportLevel < 0) {
        if (logging) {
          logger.fine("excluded content format: " + format.getName());
        }
        throw new SkippedDocumentException("Excluded by content type: "
                                           + mimetype);
      }
      if (supportLevel == 0) {
        if (logging) {
          logger.fine("unindexable content format: " + format.getName());
        }
        return false;
      }
      if (contentSize > traversalContext.maxDocumentSize()) {
        if (logging) {
          logger.fine("content is too large: " + contentSize);
        }
        return false;
      }
    } else {
      if (!format.canIndex()) {
        if (logging) {
          logger.fine("unindexable content format: " + format.getName());
        }
        return false;
      }
      if (contentSize > MAX_CONTENT_SIZE) {
        if (logging) {
          logger.fine("content is too large: " + contentSize);
        }
        return false;
      }
    }
    if (contentSize <= 0) {
      if (logging) {
        logger.fine("this object has no content");
      }
      return false;
    }
    return true;
  }

  private Calendar getCalendarFromDate(Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    return calendar;
  }

  /**
   * Return the attributes for the supplied type.  Caches result to
   * avoid frequent round-trips to server.
   *
   * @param type the object type to get attributes for
   * @return type attributes for the supplied type
   * @throws RepositoryException if an error occurs retrieving the attributes
   */
  private List<String> getTypeAttributes(IType type)
      throws RepositoryException {
    Map<String, List<String>> cache = traversalManager.getTypeAttributesCache();
    String typeName = type.getName();
    if (cache.containsKey(typeName)) {
      return cache.get(typeName);
    } else {
      if (logger.isLoggable(Level.FINER)) {
        logger.finer("Processing attributes for type " + typeName);
      }

      int count = type.getTypeAttrCount();
      ArrayList<String> typeAttributes = new ArrayList<String>(count);

      Set<String> includedMeta = traversalManager.getIncludedMeta();
      Set<String> excludedMeta = traversalManager.getExcludedMeta();
      try {
        for (int i = 0; i < count; i++) {
          IAttr curAttr = type.getTypeAttr(i);
          String name = curAttr.getName();
          if ((includedMeta.isEmpty() || includedMeta.contains(name)) &&
              !excludedMeta.contains(name)) {
            typeAttributes.add(name);
            if (logger.isLoggable(Level.FINEST)) {
              logger.finest("attribute " + name + " added to the properties");
            }
          } else {
            if (logger.isLoggable(Level.FINEST)) {
              logger.finest("attribute " + name
                  + " excluded from the properties");
            }
          }
        }
      } catch (RepositoryDocumentException e) {
        logger.log(Level.WARNING, "Error fetching property names", e);
      }

      cache.put(typeName, typeAttributes);
      return typeAttributes;
    }
  }

  public Set<String> getPropertyNames() throws RepositoryException {
    Set<String> properties = null;
    if (ActionType.ADD.equals(action)) {
      fetch();
      properties = new HashSet<String>();
      properties.add(SpiConstants.PROPNAME_DISPLAYURL);
      properties.add(SpiConstants.PROPNAME_ISPUBLIC);
      properties.add(SpiConstants.PROPNAME_LASTMODIFIED);
      properties.add(SpiConstants.PROPNAME_MIMETYPE);
      properties.add(SpiConstants.PROPNAME_TITLE);

      List<String> typeAttributes = getTypeAttributes(object.getType());
      properties.addAll(typeAttributes);
    } else {
      properties = new HashSet<String>();
      properties.add(SpiConstants.PROPNAME_ACTION);
      properties.add(SpiConstants.PROPNAME_DOCID);
      properties.add(SpiConstants.PROPNAME_LASTMODIFIED);
      properties.add(OBJECT_ID_NAME);
    }
    return properties;
  }
}

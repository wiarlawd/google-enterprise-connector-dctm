package com.google.enterprise.connector.dctm;
import java.util.List;

import com.google.enterprise.connector.dctm.dfcwrap.IClientX;
import com.google.enterprise.connector.dctm.dfcwrap.ICollection;
import com.google.enterprise.connector.dctm.dfcwrap.ILoginInfo;
import com.google.enterprise.connector.dctm.dfcwrap.IQuery;
import com.google.enterprise.connector.dctm.dfcwrap.ISession;
import com.google.enterprise.connector.dctm.dfcwrap.ISessionManager;
import com.google.enterprise.connector.spi.*;

public class DctmAuthorizationManager implements AuthorizationManager {

	IClientX clientX;

	ISessionManager sessionManager;

	private String attributeName = "r_object_id";

	private String queryStringAuthoriseDefault = "select r_object_id from dm_sysobject where r_object_id in (";

	public DctmAuthorizationManager(IClientX clientX) {

		setClientX(clientX);
		setSessionManager(this.clientX.getSessionManager());

	}

	public ResultSet authorizeDocids(List docidList, String username)
			throws RepositoryException {
		int i = 0;
		IQuery query = clientX.getQuery();
		String dqlQuery = "";
		ISession session;

		session = sessionManager.getSession(sessionManager.getDocbaseName());

		ISessionManager sessionManagerUser = clientX.getLocalClient()
				.newSessionManager();
		String ticket = session.getLoginTicketForUser(username);
		ILoginInfo logInfo = clientX.getLoginInfo();
		logInfo.setUser(username);
		logInfo.setPassword(ticket);
		if (DebugFinalData.debugInEclipse) {
			System.out.println("user vaut " + username);
		}
		sessionManagerUser
				.setIdentity(sessionManager.getDocbaseName(), logInfo);
		sessionManagerUser.setDocbaseName(sessionManager.getDocbaseName());

		dqlQuery = buildQuery(docidList);
		if (DebugFinalData.debugInEclipse) {
			System.out.println("dql " + dqlQuery);
		}
		query.setDQL(dqlQuery);

		ICollection collec = query.execute(sessionManagerUser,
				IQuery.DF_READ_QUERY);
		
		SimpleResultSet simpleResultSet = new SimpleResultSet();

		String id = "";
		SimplePropertyMap simplePropertyMap ;
		while(collec.next()){
			id = collec.getString("r_object_id");
			simplePropertyMap = new SimplePropertyMap();
			simplePropertyMap.putProperty(new SimpleProperty(SpiConstants.PROPNAME_DOCID,new SimpleValue(ValueType.STRING,id)));
			simplePropertyMap.putProperty(new SimpleProperty(SpiConstants.PROPNAME_AUTH_VIEWPERMIT,new SimpleValue(ValueType.BOOLEAN,"true")));
			simpleResultSet.add(simplePropertyMap);
			if (DebugFinalData.debugInEclipse) {
				System.out.println("id " + id);
				System.out.println("hasRight?  " + true);
			}
			docidList.remove(id);
		}

		for (i = 0; i < docidList.size(); i++) {
			id = docidList.get(i).toString();
			simplePropertyMap = new SimplePropertyMap();
			simplePropertyMap.putProperty(new SimpleProperty(SpiConstants.PROPNAME_DOCID,new SimpleValue(ValueType.STRING,id)));
			simplePropertyMap.putProperty(new SimpleProperty(SpiConstants.PROPNAME_AUTH_VIEWPERMIT,new SimpleValue(ValueType.BOOLEAN,"false")));
			simpleResultSet.add(simplePropertyMap);
			if (DebugFinalData.debugInEclipse) {
				System.out.println("docid from docidList : "
						+ docidList.get(i).toString());
				System.out.println("hasRight?  " + false);
			}
		}
		return simpleResultSet;
	}

	private String buildQuery(List docidList) {
		int i;
		String queryString;

		queryString = queryStringAuthoriseDefault;
		if (DebugFinalData.debugInEclipse) {
			System.out.println("queryString avant boucle " + queryString);
		}

		for (i = 0; i < docidList.size() - 1; i++) {
			queryString += "'" + docidList.get(i).toString() + "', ";

		}
		queryString += "'" + docidList.get(i).toString() + "')";
		if (DebugFinalData.debugInEclipse) {
			System.out.println("queryString apr�s boucle " + queryString);
		}

		return queryString;
	}

	public ResultSet authorizeTokens(List tokenList, String username)
			throws RepositoryException {
		ResultSet responses = null;
		return responses;
	}

	public IClientX getClientX() {
		return clientX;
	}

	public void setClientX(IClientX clientX) {
		this.clientX = clientX;
	}

	public ISessionManager getSessionManager() {
		return sessionManager;
	}

	private void setSessionManager(ISessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}

	protected String getAttributeName() {
		return attributeName;
	}

	protected String getQueryStringAuthoriseDefault() {
		return queryStringAuthoriseDefault;
	}

	protected void setQueryStringAuthoriseDefault(
			String queryStringAuthoriseDefault) {
		this.queryStringAuthoriseDefault = queryStringAuthoriseDefault;
	}

}

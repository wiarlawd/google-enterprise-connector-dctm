package com.google.enterprise.connector.dctm;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;

public class DctmConnector implements Connector {

	private String login;

	private String password;

	private String docbase;

	// /private String client;
	private String clientX;

	private String queryStringUnboundedDefault;

	private String queryStringBoundedDefault;

	private String queryStringAuthoriseDefault;

	private String attributeName;

	private String webtopServerUrl;

	/**
	 * Setters and getters for the data retrieved from Spring
	 */
	public void setLogin(String login) {
		this.login = login;
	}

	public String getLogin() {
		return login;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPassword() {
		return password;
	}

	public void setDocbase(String docbase) {
		this.docbase = docbase;
	}

	public String getDocbase() {
		return docbase;
	}

	public void setQueryStringUnboundedDefault(String qsud) {
		this.queryStringUnboundedDefault = qsud;
	}

	public String getQueryStringUnboundedDefault() {
		return queryStringUnboundedDefault;
	}

	public void setQueryStringBoundedDefault(String qsbd) {
		this.queryStringBoundedDefault = qsbd;
	}

	public String getQueryStringBoundedDefault() {
		return queryStringBoundedDefault;
	}

	public void setQueryStringAuthoriseDefault(String qsad) {
		this.queryStringAuthoriseDefault = qsad;
	}

	public String getQueryStringAuthoriseDefault() {
		return queryStringAuthoriseDefault;
	}

	public void setWebtopServerUrl(String wsu) {
		this.webtopServerUrl = wsu;
	}

	public String getWebtopServerUrl() {
		return webtopServerUrl;
	}

	public void setAttributeName(String an) {
		this.attributeName = an;
	}

	public String getAttributeName() {
		return attributeName;
	}

	// /public void setClient(String client) /*throws RepositoryException*/ {
	/*
	 * boolean repoExcep = false; Throwable rootCause=null; String message="";
	 * StackTraceElement[] stack = null; IClient cl = null; try { cl = (IClient)
	 * Class.forName(client).newInstance(); } catch (InstantiationException e) {
	 * repoExcep=true; rootCause=e.getCause(); message=e.getMessage();
	 * stack=e.getStackTrace(); } catch (IllegalAccessException e) {
	 * repoExcep=true; rootCause=e.getCause(); message=e.getMessage();
	 * stack=e.getStackTrace(); } catch (ClassNotFoundException e) {
	 * repoExcep=true; rootCause=e.getCause(); message=e.getMessage();
	 * stack=e.getStackTrace(); } if (repoExcep) { RepositoryException re = new
	 * RepositoryException(message,rootCause); re.setStackTrace(stack); throw
	 * re; }
	 */
	// /this.client = client;
	// /}
	// /public String getClient() {return client/*.getClass().getName()*/;}
	public void setClientX(String clientX) /* throws RepositoryException */{

		this.clientX = clientX;
	}

	public String getClientX() {
		return clientX/* .getClass().getName() */;
	}

	public DctmConnector() {
		;
	}

	public Session login() throws RepositoryException {
		System.out.println("--- DctmConnector login ---");
		if (DebugFinalData.debug) {
			OutputPerformances
					.setPerfFlag(
							this,
							"DctmConnector.login() :\n\t\t\t\t Instantiates a new DctmSession from 9 String (~250 chars) and :");
		}
		Session sess = null;

		if (!(clientX == null || login == null || password == null || docbase == null)) {

			sess = new DctmSession(clientX, login, password, docbase,
					queryStringUnboundedDefault, queryStringBoundedDefault,
					queryStringAuthoriseDefault, attributeName, webtopServerUrl);
		} else {
			sess = new DctmSession();
		}
		if (DebugFinalData.debug)
			OutputPerformances.endFlag(this,
					"return Session from DctmConnector.login()");
		return (sess);
	}

}

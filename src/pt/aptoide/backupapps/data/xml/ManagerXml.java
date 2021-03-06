/**
 * ManagerXml,		auxilliary class to Aptoide's ServiceData
 * Copyright (C) 2011  Duarte Silveira
 * duarte.silveira@caixamagica.pt
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package pt.aptoide.backupapps.data.xml;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import pt.aptoide.backupapps.R;
import pt.aptoide.backupapps.data.AptoideServiceData;
import pt.aptoide.backupapps.data.cache.ViewCache;
import pt.aptoide.backupapps.data.database.ManagerDatabase;
import pt.aptoide.backupapps.data.display.ViewDisplayListRepos;
import pt.aptoide.backupapps.data.display.ViewDisplayListsDimensions;
import pt.aptoide.backupapps.data.listeners.ViewMyapp;
import pt.aptoide.backupapps.data.model.ViewRepository;
import pt.aptoide.backupapps.data.notifications.EnumNotificationTypes;
import pt.aptoide.backupapps.data.notifications.ViewNotification;
import pt.aptoide.backupapps.data.util.Constants;

import android.util.Log;


/**
 * ManagerXml, models xml parsing
 * 
 * @author dsilveira
 * @since 3.0
 *
 */
public class ManagerXml{

	AptoideServiceData serviceData;
	public ParserDOMSmallRequests dom;
	
	/** Ongoing */
	private HashMap<Integer, ViewXmlParse> xmlParseViews;
	
	/** Object reuse pool */
	private ArrayList<ViewXmlParse> xmlParseViewsPool;
	
	public synchronized ViewXmlParse getNewViewRepoXmlParse(ViewRepository repository, ViewCache cache, ViewNotification notification){
		ViewXmlParse xmlParseView;
		if(xmlParseViewsPool.isEmpty()){
			xmlParseView = new ViewXmlParse(repository, cache, notification);
		}else{
			ViewXmlParse viewXmlParse = xmlParseViewsPool.remove(Constants.FIRST_ELEMENT);
			viewXmlParse.reuse(repository, cache, notification);
			xmlParseView = viewXmlParse;
		}
		xmlParseViews.put(notification.getNotificationHashid(), xmlParseView);	//TODO check for concurrency issues
		return xmlParseView;
	}
	
	
	public ManagerXml(AptoideServiceData serviceData) {
		this.serviceData = serviceData;
		this.dom = new ParserDOMSmallRequests(this);
		
		xmlParseViews = new HashMap<Integer, ViewXmlParse>();
		xmlParseViewsPool = new ArrayList<ViewXmlParse>();
	}

	
	public ManagerDatabase getManagerDatabase(){
		return serviceData.getManagerDatabase();
	}
	
	
	
	public void latestVersionInfoParse(ViewCache cache){	//TODO use notification 
		ViewNotification notification = serviceData.getManagerNotifications().getNewViewNotification(EnumNotificationTypes.GET_UPDATE
									, serviceData.getString(R.string.self_update), R.string.self_update);
		DefaultHandler latestVersionInfoParser = null;
	    try {
	    	XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
	    	latestVersionInfoParser = new ParserLatestVersionInfo(this, cache);
	    	
	    	xmlReader.setContentHandler(latestVersionInfoParser);
	    	xmlReader.setErrorHandler(latestVersionInfoParser);
	    	
	    	InputSource inputSource = new InputSource(new FileReader(cache.getFile()));
	    	Log.d("Aptoide-managerXml", cache.getLocalPath());
	    	xmlReader.parse(inputSource);
	    	
	    } catch (Exception e){
	    	e.printStackTrace();
	    }		
	}
	
	public void parsingLatestVersionInfoFinished(ViewLatestVersionInfo latestVersionInfo){
		serviceData.parsingLatestVersionInfoFinished(latestVersionInfo);
	}
	

	public void repoParse(ViewRepository repository, ViewCache cache, EnumInfoType infoType){
		String repoName = repository.getUri().substring(Constants.SKIP_URI_PREFIX).split("\\.")[Constants.FIRST_ELEMENT];
		
		ViewNotification notification = serviceData.getManagerNotifications().getNewViewNotification(EnumNotificationTypes.REPO_UPDATE, repoName, repository.getHashid());
		ViewXmlParse parseInfo = getNewViewRepoXmlParse(repository, cache, notification);
		DefaultHandler repoParser = null;
	    try {
	    	XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
	    	switch (infoType) {
				case DELTA:
					repoParser = new ParserRepoDelta(this, parseInfo);
					break;
					
				case BARE:
					repoParser = new ParserRepoBare(this, parseInfo);
					break;
					
				case ICON:
					notification.setProgressCompletionTarget(parseInfo.getRepository().getSize());
					repoParser = new ParserRepoIcon(this, parseInfo);
					break;	
					
				case DOWNLOAD:
					notification.setProgressCompletionTarget(parseInfo.getRepository().getSize());
					repoParser = new ParserRepoDownload(this, parseInfo);
					break;
				
				case STATS:
					notification.setProgressCompletionTarget(parseInfo.getRepository().getSize());
					repoParser = new ParserRepoStats(this, parseInfo);
					break;
					
//				case EXTRAS:
//					notification.setProgressCompletionTarget(parseInfo.getRepository().getSize());
//					repoParser = new RepoExtrasParser(this, parseInfo);
//					break;
	
				default:
					break;
			}
	    	
	    	xmlReader.setContentHandler(repoParser);
	    	xmlReader.setErrorHandler(repoParser);
	    	
	    	InputSource inputSource = new InputSource(new FileReader(new File(parseInfo.getLocalPath())));
	    	Log.d("Aptoide-managerXml", parseInfo.getLocalPath());
	    	xmlReader.parse(inputSource);
	    	
	    } catch (Exception e){
	    	e.printStackTrace();
	    }
	}


	public void repoDeltaParse(ViewRepository repository, ViewCache cache){
		if(cache != null){
			repoParse(repository, cache, EnumInfoType.DELTA);
		}
	}
	
	public void parsingRepoDeltaFinished(ViewRepository repository, int repoSizeDifferential){
		serviceData.parsingRepoDeltaFinished(repository, repoSizeDifferential);
	}


	public void repoBareParse(ViewRepository repository, ViewCache cache){
		if(cache != null){
			repoParse(repository, cache, EnumInfoType.BARE);
		}
	}
	
	public void parsingRepoBareFinished(ViewRepository repository){
		serviceData.parsingRepoBareFinished(repository);
	}

	public void repoDownloadParse(ViewRepository repository, ViewCache cache){
		if(cache != null){
			repoParse(repository, cache, EnumInfoType.DOWNLOAD);
		}
	}
	
	public void parsingRepoDownloadFinished(ViewRepository repository){
		serviceData.parsingRepoDownloadInfoFinished(repository);
	}
	
	public void addRepoIconsInfo(ViewRepository repository){
		serviceData.addRepoIconsInfo(repository);
	}
	
	public void repoIconParse(ViewRepository repository, ViewCache cache){
		if(cache != null){
			repoParse(repository, cache, EnumInfoType.ICON);
		}
	}
	
	public void parsingRepoIconsFinished(ViewRepository repository){
		serviceData.parsingRepoIconsFinished(repository);
	}
	
	public void repoStatsParse(ViewRepository repository, ViewCache cache){
		if(cache != null){
			repoParse(repository, cache, EnumInfoType.STATS);
		}
	}
	
	public void parsingRepoStatsFinished(ViewRepository repository){
		serviceData.parsingRepoStatsFinished(repository);
	}
	
//	public void repoExtrasParse(ViewRepository repository, ViewCache cache){
//		repoAppParse(repository, cache, EnumInfoType.EXTRAS);
//	}
	
	public void parsingRepoExtrasFinished(ViewRepository repository){
//		serviceData.parsingRepoExtrasFinished(repository);
	}
	
	
	
	public void repoAppParse(ViewRepository repository, ViewCache cache, int appHashid, EnumInfoType infoType){
		String repoName = repository.getUri().substring(Constants.SKIP_URI_PREFIX).split("\\.")[Constants.FIRST_ELEMENT];
		
		ViewNotification notification = serviceData.getManagerNotifications().getNewViewNotification(EnumNotificationTypes.REPO_APP_UPDATE, repoName, appHashid);
		ViewXmlParse parseInfo = getNewViewRepoXmlParse(repository, cache, notification);
		DefaultHandler repoParser = null;
	    try {
	    	XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
	    	switch (infoType) {
//				case ICON:
//					notification.setProgressCompletionTarget(parseInfo.getRepository().getSize());
//					repoParser = new RepoIconParser(this, parseInfo);
//					break;	
					
				case DOWNLOAD:
//					notification.setProgressCompletionTarget(parseInfo.getRepository().getSize());
					repoParser = new ParserRepoDownload(this, parseInfo, appHashid);
					break;
				
				case STATS:
//					notification.setProgressCompletionTarget(parseInfo.getRepository().getSize());
					repoParser = new ParserRepoStats(this, parseInfo, appHashid);
					break;
					
				case EXTRAS:
//					notification.setProgressCompletionTarget(parseInfo.getRepository().getSize());
					repoParser = new ParserRepoExtras(this, parseInfo, appHashid);
					break;
					
				case COMMENTS:
					repoParser = new ParserComments(this, parseInfo, appHashid);
					break;
	
				default:
					break;
			}
	    	
	    	xmlReader.setContentHandler(repoParser);
	    	xmlReader.setErrorHandler(repoParser);
	    	
	    	InputSource inputSource = new InputSource(new FileReader(new File(parseInfo.getLocalPath())));
	    	Log.d("Aptoide-managerXml", parseInfo.getLocalPath());
	    	xmlReader.parse(inputSource);
	    	
	    } catch (Exception e){
	    	e.printStackTrace();
	    }
	}
	
	public void repoAppDownloadParse(ViewRepository repository, ViewCache cache, int appHashid){
		repoAppParse(repository, cache, appHashid, EnumInfoType.DOWNLOAD);
	}
	
	public void parsingRepoAppDownloadFinished(ViewRepository repository, int appHashid){
		serviceData.parsingRepoAppDownloadInfoFinished(repository, appHashid);
	}
	
	public void repoAppStatsParse(ViewRepository repository, ViewCache cache, int appHashid){
		repoAppParse(repository, cache, appHashid, EnumInfoType.STATS);
	}
	
	public void repoAppCommentsParse(ViewRepository repository, ViewCache cache, int appHashid){
		repoAppParse(repository, cache, appHashid, EnumInfoType.COMMENTS);
	}
	
	public void parsingRepoAppStatsFinished(ViewRepository repository, int appHashid){
		serviceData.parsingRepoAppStatsFinished(repository, appHashid);
	}
	
	public void repoAppExtrasParse(ViewRepository repository, ViewCache cache, int appHashid){
		repoAppParse(repository, cache, appHashid, EnumInfoType.EXTRAS);
	}
	
	public void parsingRepoAppExtrasFinished(ViewRepository repository, int appHashid){
		serviceData.parsingRepoAppExtrasFinished(repository, appHashid);
	}
	
	public void myappParse(ViewCache cache, String myappName){	//TODO use notification 
		ViewNotification notification = serviceData.getManagerNotifications().getNewViewNotification(EnumNotificationTypes.PARSE_MYAPP, myappName, myappName.hashCode());
		DefaultHandler myappParser = null;
	    try {
	    	XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
	    	myappParser = new ParserMyapp(this, cache);
	    	
	    	xmlReader.setContentHandler(myappParser);
	    	xmlReader.setErrorHandler(myappParser);
	    	
	    	InputSource inputSource = new InputSource(new FileReader(cache.getFile()));
	    	Log.d("Aptoide-managerXml", cache.getLocalPath());
	    	xmlReader.parse(inputSource);
	    	
	    } catch (Exception e){
	    	e.printStackTrace();
	    }		
	}
	
	public void parsingMyappFinished(ViewMyapp viewMyapp, ViewDisplayListRepos listRepos){
		serviceData.parsingMyappFinished(viewMyapp, listRepos);
	}
	
	public ViewDisplayListsDimensions getDisplayListsDimensions(){
		return serviceData.getDisplayListsDimensions();
	}
	
}

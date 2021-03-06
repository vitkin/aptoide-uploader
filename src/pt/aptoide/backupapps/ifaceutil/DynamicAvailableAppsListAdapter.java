/**
 * DynamicAvailableAppsListAdapter,		part of Aptoide's data model
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

package pt.aptoide.backupapps.ifaceutil;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import pt.aptoide.backupapps.EnumAppsLists;
import pt.aptoide.backupapps.EnumAptoideInterfaceTasks;
import pt.aptoide.backupapps.R;
import pt.aptoide.backupapps.data.display.ViewDisplayApplication;
import pt.aptoide.backupapps.data.display.ViewDisplayApplicationBackup;
import pt.aptoide.backupapps.data.display.ViewDisplayCategory;
import pt.aptoide.backupapps.data.display.ViewDisplayListApps;
import pt.aptoide.backupapps.data.display.ViewDisplayListsDimensions;
import pt.aptoide.backupapps.data.model.ViewListIds;
import pt.aptoide.backupapps.data.util.Constants;
import pt.aptoide.backupapps.data.AIDLAptoideServiceData;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

 /**
 * DynamicAvailableAppsListAdapter, models a dynamic loading, available apps list adapter
 * 									extends BaseAdapter implements InterfaceAvailableAppsAdapter
 * 
 * @author dsilveira
 * @since 3.0
 *
 */
public class DynamicAvailableAppsListAdapter extends BaseAdapter implements InterfaceAvailableAppsAdapter{
	
	private Context context;

	ImageLoader imageLoader;
	
	private ListView listView;
	private LayoutInflater layoutInflater;
	
	private View topProgressBar;
	private View bottomProgressBar;
	
	private ViewDisplayListsDimensions displayListsDimensions;
	private ViewDisplayCategory category = null;
	
//	private int totalAvailableApps = Constants.EMPTY_INT;
	
	private ViewDisplayListApps apps = null;
	private ViewDisplayListApps freshApps = null;
	private ViewDisplayListApps freshTopApps = null;
	private ViewDisplayListApps freshBottomApps = null;
//	private AtomicInteger appsTrimTopAmount;
//	private AtomicInteger appsTrimBottomAmount;
	
	private AtomicInteger cacheListOffset;
	private AtomicInteger cacheAppsTrimmed;

	private boolean headerFooterSet = false;
	
	private AtomicInteger globalPosition;
	private AtomicInteger scrollDirectionOrigin;
	private AtomicBoolean directionDown;
	private AtomicBoolean changingList;
	
	private AtomicBoolean sleep;
	
	private AtomicInteger reloadListScrollPosition;

	private AvailableAppsManager appsManager;
	
	private AIDLAptoideServiceData serviceDataCaller = null;
	private Handler aptoideTasksHandler;

	public ArrayList<Integer> selectionsSavedState;

	
	private Handler interfaceTasksHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	EnumAptoideInterfaceTasks task = EnumAptoideInterfaceTasks.reverseOrdinal(msg.what);
        	switch (task) {
				
				case RESET_AVAILABLE_LIST_DISPLAY:
					resetDisplay();
					break;
					
//				case TRIM_TOP_AND_UPDATE_AVAILABLE_LIST_DISPLAY:
//					trimTopAppsList(appsTrimTopAmount.get());					
//					break;
//					
//				case TRIM_BOTTOM_AND_UPDATE_AVAILABLE_LIST_DISPLAY:
//					trimBottomAppsList(appsTrimBottomAmount.get());					
//					break;
					
				case PREPEND_AND_UPDATE_AVAILABLE_LIST_DISPLAY:
					prependAndUpdateDisplay(freshTopApps);
					break;
					
				case APPEND_AND_UPDATE_AVAILABLE_LIST_DISPLAY:
					appendAndUpdateDisplay(freshBottomApps);
					break;
					
				case AVAILABLE_UNSET_LOADING_HEADER:
					setLoadingHeader(false);					
					break;
					
				case AVAILABLE_UNSET_LOADING_FOOTER:
					setLoadingFooter(false);					
					break;
				
					
//				case TRIM_PREPEND_AND_UPDATE_AVAILABLE_LIST_DISPLAY:
//					trimPrependAndUpdateDisplay(freshTopApps, appsTrimBottomAmount.get());
//					break;
//					
//				case TRIM_APPEND_AND_UPDATE_AVAILABLE_LIST_DISPLAY:
//					trimAppendAndUpdateDisplay(appsTrimTopAmount.get(), freshBottomApps);
//					break;
	
				default:
					break;
			}
        }
    };
    
    

    
    
   
    
    private class AvailableAppsManager{
    	
    	private ExecutorService dataColector;


    	public AvailableAppsManager() {
    		dataColector = Executors.newSingleThreadExecutor();
    	}
		

		/** 
		 * scrollDown - MOVE FORWARD IN AVAILABLE APPS LIST
		 * 
		 */
		public void scrollDown(){
			try {
				dataColector.execute(new ScrollDown());
			} catch (Exception e) { }
		}

		/** 
		 * scrollUp - MOVE BACKWARD IN AVAILABLE APPS LIST
		 * 
		 */
		public void scrollUp(){
			try {
				dataColector.execute(new ScrollUp());
			} catch (Exception e) { }
		}

		/** 
		 * reload - RELOAD AVAILABLE APPS
		 * 
		 */
		public void reload(){
			try {
				dataColector.execute(new Reload());
			} catch (Exception e) { }
		}

		/** 
		 * reset - RESET AVAILABLE APPS TO ZERO
		 * 
		 */
		public void reset(){
			try {
				dataColector.execute(new Reset());
			} catch (Exception e) { }
		}
    	
		private class ScrollDown implements Runnable {

			@Override
			public void run() {
				int range = displayListsDimensions.getPageSize();
				
//				boolean trim_top = (apps.size() >= (displayListsDimensions.getCacheSize()));
//				if(trim_top){
//					cacheAppsTrimmed.incrementAndGet();
//				}
				boolean append = true;
				int offset = (cacheListOffset.get()+1)*displayListsDimensions.getPageSize();

				try {
					if( category != null && !category.hasChildren()){
						Log.d("Aptoide","scrolling down available list.  offset: "+offset+" range: "+range+" category: "+category);
						setBottomFreshAvailableApps(serviceDataCaller.callGetAvailableAppsByCategory(offset, range, category.getCategoryHashid()));
					}else{
						Log.d("Aptoide","scrolling down available list.  offset: "+offset+" range: "+range);
						setBottomFreshAvailableApps(serviceDataCaller.callGetAvailableApps(offset, range));
					}
					
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if(freshBottomApps.size() == 0 ){
					try {
						if( category != null && !category.hasChildren()){
							Log.d("Aptoide","scrolling down available list.  offset: "+offset+" range: "+range+" category: "+category);
							setBottomFreshAvailableApps(serviceDataCaller.callGetAvailableAppsByCategory(offset, range, category.getCategoryHashid()));
						}else{
							Log.d("Aptoide","scrolling down available list.  offset: "+offset+" range: "+range);
							setBottomFreshAvailableApps(serviceDataCaller.callGetAvailableApps(offset, range));
						}

					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					if(freshBottomApps.size() == 0 ){
						append = false;
					}
				}
				
//				if(trim_top){
//					appsTrimTopAmount.addAndGet(range);
//					if(append){
//						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.TRIM_APPEND_AND_UPDATE_AVAILABLE_LIST_DISPLAY.ordinal());
//					}else{
//						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.TRIM_TOP_AND_UPDATE_AVAILABLE_LIST_DISPLAY.ordinal());
//					}
//				}else{
					if(append){
						cacheListOffset.incrementAndGet();
						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.APPEND_AND_UPDATE_AVAILABLE_LIST_DISPLAY.ordinal());
					}else{
				    	changingList.set(false);
						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.AVAILABLE_UNSET_LOADING_FOOTER.ordinal());
					}
//				}
				
			}
			
		}
    	
		private class ScrollUp implements Runnable {

			@Override
			public void run() {
				int range = displayListsDimensions.getPageSize();
				
//				boolean trim_bottom = (apps.size() >= (displayListsDimensions.getCacheSize()));
//				if(trim_bottom){
//					cacheListOffset.decrementAndGet();					
//				}
				boolean prepend = (cacheAppsTrimmed.get() > 0);
				if(prepend){
					int offset = cacheAppsTrimmed.get()-displayListsDimensions.getPageSize();
					
					try {
						if(category != null && !category.hasChildren()){
							Log.d("Aptoide","scrolling up available list.  offset: "+offset+" range: "+range+" category: "+category);
							setTopFreshAvailableApps(serviceDataCaller.callGetAvailableAppsByCategory(offset, range, category.getCategoryHashid()));
						}else{
							Log.d("Aptoide","scrolling up available list.  offset: "+offset+" range: "+range);
							setTopFreshAvailableApps(serviceDataCaller.callGetAvailableApps(offset, range));
						}	
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					if(freshTopApps.size() == 0){
						try {
							if(category != null && !category.hasChildren()){
								Log.d("Aptoide","scrolling up available list.  offset: "+offset+" range: "+range+" category: "+category);
								setTopFreshAvailableApps(serviceDataCaller.callGetAvailableAppsByCategory(offset, range, category.getCategoryHashid()));
							}else{
								Log.d("Aptoide","scrolling up available list.  offset: "+offset+" range: "+range);
								setTopFreshAvailableApps(serviceDataCaller.callGetAvailableApps(offset, range));
							}	
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
//				}

//				if(trim_bottom){
//					appsTrimBottomAmount.addAndGet(range);
//					if(prepend){
//						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.TRIM_PREPEND_AND_UPDATE_AVAILABLE_LIST_DISPLAY.ordinal());						
//					}else{
//						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.TRIM_BOTTOM_AND_UPDATE_AVAILABLE_LIST_DISPLAY.ordinal());
//					}
//				}else{
//					if(prepend){
						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.PREPEND_AND_UPDATE_AVAILABLE_LIST_DISPLAY.ordinal());							
					}else{
				    	changingList.set(false);
						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.AVAILABLE_UNSET_LOADING_HEADER.ordinal());	
					}
//				}
				
			}
			
		}
    	
		private class Reload implements Runnable {
			@Override
			public void run() {
				int originalScrollPoint = listView.getFirstVisiblePosition();
				reloadListScrollPosition.set(originalScrollPoint%displayListsDimensions.getPageSize()); 
				cacheListOffset.set(((cacheAppsTrimmed.get()+originalScrollPoint)/displayListsDimensions.getPageSize())-1);
				cacheAppsTrimmed.set(cacheAppsTrimmed.get()+(originalScrollPoint/displayListsDimensions.getPageSize())-1);
				changingList.set(false);
				scrollDirectionOrigin.set(reloadListScrollPosition.get());
				globalPosition.set(0);
				int offset =  cacheListOffset.get()*displayListsDimensions.getPageSize();
				int range = displayListsDimensions.getPageSize();
								
				try {
					if(category != null && !category.hasChildren()){
						Log.d("Aptoide","reloading available list.  offset: "+offset+" range: "+range);
						setFreshAvailableApps(serviceDataCaller.callGetAvailableAppsByCategory(offset, range, category.getCategoryHashid()));
						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.RESET_AVAILABLE_LIST_DISPLAY.ordinal());
					}else{
						Log.d("Aptoide","reloading available list.  offset: "+offset+" range: "+range);
						setFreshAvailableApps(serviceDataCaller.callGetAvailableApps(offset, range));
						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.RESET_AVAILABLE_LIST_DISPLAY.ordinal());
					}
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
    	
		private class Reset implements Runnable {

			@Override
			public void run() {
				scrollDirectionOrigin.set(0);
				globalPosition.set(0);
				cacheListOffset.set(0);
				cacheAppsTrimmed.set(0);
				int offset = 0;
				int range = displayListsDimensions.getPageSize();
				try {
					if(category != null && !category.hasChildren()){
						Log.d("Aptoide","resetting available list.  offset: "+offset+" range: "+range+" "+category);
						setFreshAvailableApps(serviceDataCaller.callGetAvailableAppsByCategory(offset, range, category.getCategoryHashid()));
						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.RESET_AVAILABLE_LIST_DISPLAY.ordinal());
					}else{
						Log.d("Aptoide","resetting available list.  offset: "+offset+" range: "+range);
						setFreshAvailableApps(serviceDataCaller.callGetAvailableApps(offset, range));
						interfaceTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.RESET_AVAILABLE_LIST_DISPLAY.ordinal());
					}
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}

    }
    
    
	private synchronized void detectMoreAppsNeeded(int position){
		globalPosition.set(position);
		directionDown.set((position - scrollDirectionOrigin.get()) > 0);
		
//		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "detecting cacheIncrease, position: "+position+" origin: "+scrollDirectionOrigin.get()
//				+" down: "+directionDown+" endBuffer: "+(getCount()-position)+" trim: "+cacheAppsTrimmed.get()+" trim+count: "+(cacheAppsTrimmed.get()+getCount())
//				+" intrigger: "+displayListsDimensions.getIncreaseTrigger()+" detrigger: "+displayListsDimensions.getDecreaseTrigger());
		
		if( directionDown.get() /*&& (position-scrollDirectionOrigin.get()) > displayListsDimensions.getPageSize() && (cacheAppsTrimmed.get()+getCount()) != totalAvailableApps*/ && (getCount()-position) < displayListsDimensions.getIncreaseTrigger() ){
			sleep.set(false);
			setLoadingFooter(true);
			if(!changingList.get()){
				scrollDirectionOrigin.set(position);
				changingList.set(true);
				Log.d("Aptoide-DynamicAvailableAppsListAdapter", "scrollind down, position: "+position);
				appsManager.scrollDown();
//				if(position > displayListsDimensions.getDecreaseTrigger()){
//					trimTopAppsList(displayListsDimensions.getIncreaseTrigger());
//				}
			}else if( (getCount()-position) < (displayListsDimensions.getPageSize()/Constants.DISPLAY_LISTS_PAGE_SIZE_MULTIPLIER) ){
				scrollDirectionOrigin.set(position);
			}
		}else if( !directionDown.get() && cacheAppsTrimmed.get() > 0 /*&& (scrollDirectionOrigin.get()-position) > displayListsDimensions.getPageSize() && (cacheAppsTrimmed.get()+getCount()) > displayListsDimensions.getCacheSize()*/ && position < displayListsDimensions.getIncreaseTrigger() ){
//			Log.d("Aptoide-DynamicAvailableAppsListAdapter", "sroll up, possible: "+position);
			sleep.set(false);
			setLoadingHeader(true);
			if(!changingList.get()){
				scrollDirectionOrigin.set(position);
				changingList.set(true);
				Log.d("Aptoide-DynamicAvailableAppsListAdapter", "srolling up, position: "+position);
				appsManager.scrollUp();
//				if((getCount()-position) > displayListsDimensions.getDecreaseTrigger()){
//					trimBottomAppsList(displayListsDimensions.getIncreaseTrigger());
//				}
			}else if( position < (displayListsDimensions.getPageSize()/Constants.DISPLAY_LISTS_PAGE_SIZE_MULTIPLIER) ){
				scrollDirectionOrigin.set(position);
			}
		}
	}
	
	
	public void setLoadingHeader(boolean on){
//		if(on){//&& globalPosition.get() < (displayListsDimensions.getPageSize()/Constants.DISPLAY_LISTS_PAGE_SIZE_MULTIPLIER )){
//			int scrollRestorePosition = listView.getFirstVisiblePosition();
//			int partialScrollRestorePosition = (listView.getChildAt(0)==null?0:listView.getChildAt(0).getTop());
//		
//			topProgressBar.setVisibility(View.VISIBLE);
//
//	    	listView.setSelectionFromTop(scrollRestorePosition, partialScrollRestorePosition);
//		}else{
//			topProgressBar.setVisibility(View.GONE);
//		}
	}
	
	public void setLoadingFooter(boolean on){
//		if(on){
//			bottomProgressBar.setVisibility(View.VISIBLE);
//		}else{
//			bottomProgressBar.setVisibility(View.GONE);
//		}
	}
	
	
	
	public static class AvailableRowViewHolder{
		ImageView app_icon;
		
		TextView app_name;
		TextView version_name;

		TextView timestamp;
		TextView status;
		TextView size;
		
		CheckBox check;
		
		public void setChecked(boolean checked){
			check.setChecked(checked);
		}
	}
	

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		
		AvailableRowViewHolder rowViewHolder;
		
		if(convertView == null){
			convertView = layoutInflater.inflate(R.layout.row_app_backup, null);
			
			rowViewHolder = new AvailableRowViewHolder();
			rowViewHolder.app_icon = (ImageView) convertView.findViewById(R.id.app_icon);
			rowViewHolder.app_name = (TextView) convertView.findViewById(R.id.app_name);
			rowViewHolder.version_name = (TextView) convertView.findViewById(R.id.version_name);

			rowViewHolder.timestamp = (TextView) convertView.findViewById(R.id.timestamp);
			
			rowViewHolder.status = (TextView) convertView.findViewById(R.id.status);
			rowViewHolder.size = (TextView) convertView.findViewById(R.id.size);
			
			rowViewHolder.check = (CheckBox) convertView.findViewById(R.id.check);
			
			convertView.setTag(rowViewHolder);
		}else{
			rowViewHolder = (AvailableRowViewHolder) convertView.getTag();
		}
		
//		File iconCache = new File(apps.get(position).getIconCachePath());
//		if(iconCache.exists() && iconCache.length() > 0){
//			rowViewHolder.app_icon.setImageURI(Uri.parse(apps.get(position).getIconCachePath()));
//		}else{
//			rowViewHolder.app_icon.setImageResource(android.R.drawable.sym_def_app_icon);
//		}
		imageLoader.DisplayImage(apps.get(position).getIconCachePath(), rowViewHolder.app_icon);
		
		rowViewHolder.app_name.setText(apps.get(position).getAppName());
		rowViewHolder.version_name.setText(" "+apps.get(position).getVersionName());

		rowViewHolder.timestamp.setText(((ViewDisplayApplicationBackup) apps.get(position)).getFormatedTimestamp());
		
		rowViewHolder.status.setText(((ViewDisplayApplicationBackup) apps.get(position)).getStatus().toString(context));
		rowViewHolder.size.setText(((ViewDisplayApplicationBackup) apps.get(position)).getSize());
		
		rowViewHolder.check.setChecked(((ViewDisplayApplicationBackup) apps.get(position)).isChecked());
		rowViewHolder.check.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				((ViewDisplayApplicationBackup) apps.get(position)).toggleCheck();
			}
		});
		
		
		detectMoreAppsNeeded(position);
		
		return convertView;
	}

	
	@Override
	public int getCount() {
		return apps.size();
	}

	@Override
	public ViewDisplayApplication getItem(int position) {
		return apps.get(position);
	}

	@Override
	public long getItemId(int position) {
		return apps.get(position).getAppHashid();
	}

	@Override
	public void toggleSelectAll(){
		if(!apps.isEmpty() && ((ViewDisplayApplicationBackup) apps.get(0)).isChecked()){
			unselectAll();
		}else{
			selectAll();
		}
	}

	@Override
	public void selectAll(){
		for (ViewDisplayApplication app : apps) {
			ViewDisplayApplicationBackup backup = ((ViewDisplayApplicationBackup) app);
			if(!backup.isChecked()){
				backup.toggleCheck();
			}
		}
		notifyDataSetChanged();
	}

	@Override
	public void unselectAll(){
		for (ViewDisplayApplication app : apps) {
			ViewDisplayApplicationBackup backup = ((ViewDisplayApplicationBackup) app);
			if(backup.isChecked()){
				backup.toggleCheck();
			}
		}
		notifyDataSetChanged();
	}
	
	public void saveSelectionState(){
		selectionsSavedState = new ArrayList<Integer>();
		int i;
		ViewDisplayApplication app;
		for (i=0; i<apps.size(); i++) {
			app = apps.get(i);
			ViewDisplayApplicationBackup backup = ((ViewDisplayApplicationBackup) app);
			if(backup.isChecked()){
				selectionsSavedState.add(i);
			}
		}
	}
	
	public void restoreSelectedState(){
		for (Integer selected : selectionsSavedState) {
			((ViewDisplayApplicationBackup) apps.get(selected)).toggleCheck();
		}
	}

	@Override
	public ViewListIds getSelectedIds(){
		ViewListIds selected = new ViewListIds();
		for (ViewDisplayApplication app: apps) {
			ViewDisplayApplicationBackup backup = ((ViewDisplayApplicationBackup) app);
			if(backup.isChecked()){
				selected.add(backup.getAppHashid());
			}
		}
		return selected;
	}
	
	
	
	/**
	 * DynamicAppsListAdapter Constructor
	 *
	 * @param context
	 * @param textViewResourceId
	 */
	public DynamicAvailableAppsListAdapter(Context context, ListView listView, AIDLAptoideServiceData serviceDataCaller, Handler interfaceTasksHandler) {
		
		this.context = context;
		
		imageLoader = new ImageLoader(context);
		
		this.serviceDataCaller = serviceDataCaller;
		this.aptoideTasksHandler = interfaceTasksHandler;
		
		apps = new ViewDisplayListApps();
		freshApps = new ViewDisplayListApps();
		freshBottomApps = new ViewDisplayListApps();
		freshTopApps = new ViewDisplayListApps();

		appsManager = new AvailableAppsManager();


		this.listView = listView;
		layoutInflater = LayoutInflater.from(context);
		
		LayoutInflater progressBarLayoutInflater = LayoutInflater.from(context);
		bottomProgressBar=progressBarLayoutInflater.inflate(R.layout.progress_bar, null); 
		topProgressBar=progressBarLayoutInflater.inflate(R.layout.progress_bar, null);
		
//		if(!headerFooterSet){
//			headerFooterSet=true;
//			listView.addFooterView(bottomProgressBar);
//			listView.addHeaderView(topProgressBar);
//		}
		
//		setLoadingHeader(false);
//		setLoadingFooter(false);
		
		
		cacheListOffset = new AtomicInteger(0);
		cacheAppsTrimmed = new AtomicInteger(0);
		cacheAppsTrimmed = new AtomicInteger(0);
//		appsTrimBottomAmount = new AtomicInteger(0);
		
		globalPosition = new AtomicInteger(0);
		scrollDirectionOrigin = new AtomicInteger(0);
		directionDown = new AtomicBoolean(true);
		changingList = new AtomicBoolean(false);
		
		sleep = new AtomicBoolean(true);
		
		reloadListScrollPosition = new AtomicInteger(0);

        try {

            displayListsDimensions = serviceDataCaller.callGetDisplayListsDimensions();
        } catch (RemoteException e) {
			// TODO Auto-generated catch block
            e.printStackTrace();
        }
        
	} 

	@Override
	public void resetDisplayAvailable(){
		aptoideTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.SWITCH_AVAILABLE_TO_PROGRESSBAR.ordinal());
		sleep.set(true);
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "external reset, sleep: "+sleep.get());
		this.apps.clear();
		
		appsManager.reset();		
	}
	
	public void resetDisplay(ViewDisplayCategory category){
		aptoideTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.SWITCH_AVAILABLE_TO_PROGRESSBAR.ordinal());
		sleep.set(true);
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "external reset, sleep: "+sleep.get());
		this.category = category;
		this.apps.clear();
//		try {
//			if(category != null && category.getCategoryHashid() != Constants.TOP_CATEGORY){
//				totalAvailableApps = serviceDataCaller.callGetTotalAvailableAppsInCategory(category.getCategoryHashid());
//			}else{
//				totalAvailableApps = serviceDataCaller.callGetTotalAvailableApps();
//			}
//		} catch (RemoteException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		appsManager.reset();
	}	
    
	public void reloadDisplay(){
		sleep.set(true);
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "external reload, sleep: "+sleep.get());
//		aptoideTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.SWITCH_AVAILABLE_TO_PROGRESSBAR.ordinal());
//		apps.clear();
//		appsManager.reload();
//		listView.destroyDrawingCache();
		resetDisplay(this.category);
	}

	@Override
	public void refreshDisplayAvailable(){
		notifyDataSetChanged();
	}
	
	public void sleep(){
		sleep.set(true);
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "external sleep, sleep: "+sleep.get());
	}
	
	
	
	private synchronized void setFreshAvailableApps(ViewDisplayListApps freshAvailableApps){
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "setFreshAvailableList "+freshAvailableApps.size()+" count: "+getCount()+" cachesize: "+displayListsDimensions.getCacheSize());
		this.freshApps = freshAvailableApps;
	}
	
	private synchronized void setTopFreshAvailableApps(ViewDisplayListApps freshAvailableApps){
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "addTopFreshAvailableList "+freshAvailableApps.size()+" count: "+getCount()+" cachesize: "+displayListsDimensions.getCacheSize());
		this.freshTopApps = freshAvailableApps;
	}
	
	private synchronized void setBottomFreshAvailableApps(ViewDisplayListApps freshAvailableApps){
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "addBottomFreshAvailableList "+freshAvailableApps.size()+" count: "+getCount()+" cachesize: "+displayListsDimensions.getCacheSize());
		this.freshBottomApps = freshAvailableApps;
	}
	


	private void initDisplayAvailable(){
		listView.setAdapter(this);
    }
	
	private void resetDisplay(){
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "new AvailableList: "+freshApps.size());
		
		if(freshApps.isEmpty()){
			aptoideTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.SWITCH_AVAILABLE_TO_NO_APPS.ordinal());
		}else{
			aptoideTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.SWITCH_AVAILABLE_TO_LIST.ordinal());
//	    	aptoideTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.RESET_UPDATABLE_LIST_DISPLAY.ordinal());
		}

		sleep.set(false);
		changingList.set(false);
//		int scrollRestorePosition = listView.getFirstVisiblePosition();
//		int partialScrollRestorePosition = (listView.getChildAt(0)==null?0:listView.getChildAt(0).getTop());
    	this.apps = freshApps;
		initDisplayAvailable();
		refreshDisplayAvailable();
		if(reloadListScrollPosition.get() == 0){
			listView.setSelection(1);
		}else{
			listView.setSelection(reloadListScrollPosition.get());
			reloadListScrollPosition.set(0);
		}
		if( !sleep.get() && directionDown.get() && !freshApps.isEmpty() && (getCount()-globalPosition.get()) < displayListsDimensions.getCacheSize()){
			setLoadingFooter(true);
			appsManager.scrollDown();
		}else {
			setLoadingFooter(false);
		}
//    	listView.setSelectionFromTop(scrollRestorePosition, partialScrollRestorePosition);
    	
	}
	
	private synchronized void trimTopAppsList(int trimAmount){
		cacheAppsTrimmed.addAndGet(trimAmount);
		int adjustAmount = trimAmount;
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "trimTopAvailableList: "+trimAmount);
		int scrollRestorePosition = listView.getFirstVisiblePosition();
		int partialScrollRestorePosition = (listView.getChildAt(0)==null?0:listView.getChildAt(0).getTop());
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "list size before: "+apps.size()+"   original scroll position: "+scrollRestorePosition);
		do{
			this.apps.removeFirst();
			trimAmount--;
		}while(trimAmount>0);
		refreshDisplayAvailable();
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "list size after: "+apps.size());
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "restoring scroll position, currentPosition:"+listView.getFirstVisiblePosition()+" firstVisiblePosition: "+(scrollRestorePosition-adjustAmount)+" top: "+partialScrollRestorePosition);
    	listView.setSelectionFromTop((scrollRestorePosition-adjustAmount), partialScrollRestorePosition);
    	scrollDirectionOrigin.set(globalPosition.get()-adjustAmount);
	}
	
	private synchronized void trimBottomAppsList(int trimAmount){
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "trimEndAvailableList: "+trimAmount);
		do{
			this.apps.removeLast();
			trimAmount--;
		}while(trimAmount>0);
		refreshDisplayAvailable();
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "list size after: "+apps.size());
	}
	
	private void prependAndUpdateDisplay(ViewDisplayListApps freshAvailableApps){	
		cacheAppsTrimmed.addAndGet(-freshAvailableApps.size());
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "prepending freshAvailableList: "+freshAvailableApps.size());
		if(freshAvailableApps.isEmpty()){
	    	changingList.set(false);
			setLoadingHeader(false);
			return;
		}else{
			aptoideTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.SWITCH_AVAILABLE_TO_LIST.ordinal());
		}
		
    	int adjustAmount = freshAvailableApps.size();
    	boolean newList = this.apps.isEmpty();
    	if(newList){
    		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "prepending to empty available list");
    		this.apps = freshAvailableApps;
    		
    		initDisplayAvailable();
    	}else{	
    		int scrollRestorePosition = listView.getFirstVisiblePosition();
    		int partialScrollRestorePosition = (listView.getChildAt(0)==null?0:listView.getChildAt(0).getTop());
    		
    		this.apps.addAll(0,freshAvailableApps);
    		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "new displayList size: "+this.apps.size());
    		
    		refreshDisplayAvailable();

        	listView.setSelectionFromTop(scrollRestorePosition+adjustAmount, partialScrollRestorePosition);
        	scrollDirectionOrigin.set(globalPosition.get()+adjustAmount);
    	}
		if(!sleep.get() && !directionDown.get() && cacheAppsTrimmed.get() > 0 && globalPosition.get() < displayListsDimensions.getCacheSize()){
			setLoadingHeader(true);
			appsManager.scrollUp();
			if((getCount()-globalPosition.get()) > displayListsDimensions.getDecreaseTrigger()){
				trimBottomAppsList(displayListsDimensions.getIncreaseTrigger());
			}
		}else {
	    	changingList.set(false);
			setLoadingHeader(false);
			detectMoreAppsNeeded(globalPosition.get());
		}
	}
	
	private void appendAndUpdateDisplay(ViewDisplayListApps freshAvailableApps){	
		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "appending freshAvailableList: "+freshAvailableApps.size());
		if(freshAvailableApps.isEmpty()){
			return;
		}else{
			aptoideTasksHandler.sendEmptyMessage(EnumAptoideInterfaceTasks.SWITCH_AVAILABLE_TO_LIST.ordinal());
		}
		
		boolean newList = this.apps.isEmpty();
    	if(newList){
    		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "appending to empty available list");
    		this.apps = freshAvailableApps;
    		
    		initDisplayAvailable();
    	}else{	
    		this.apps.addAll(freshAvailableApps);
    		Log.d("Aptoide-DynamicAvailableAppsListAdapter", "new displayList size: "+this.apps.size());
    		
    		refreshDisplayAvailable();
    		
    	}
		if( !sleep.get() && directionDown.get() && !freshAvailableApps.isEmpty() && (getCount()-globalPosition.get()) < displayListsDimensions.getCacheSize()){
			setLoadingFooter(true);
			appsManager.scrollDown();
			if(globalPosition.get() > displayListsDimensions.getDecreaseTrigger()){
				trimTopAppsList(displayListsDimensions.getIncreaseTrigger());
			}
		}else {
	    	changingList.set(false);
			setLoadingFooter(false);
			detectMoreAppsNeeded(globalPosition.get());
		}
	}
	
//	private void trimPrependAndUpdateDisplay(ViewDisplayListApps freshAvailableApps, int trimAmount){
//		prependAndUpdateDisplay(freshAvailableApps);
//		trimBottomAppsList(trimAmount);
//	}
//	
//	private void trimAppendAndUpdateDisplay(int trimAmount, ViewDisplayListApps freshAvailableApps){
//		trimTopAppsList(trimAmount);
//		appendAndUpdateDisplay(freshAvailableApps);
//	}


	@Override
	public boolean isDynamic() {
		return true;
	}

	@Override
	public void shutdownNow(){
		appsManager.dataColector.shutdownNow();
	}
	
}

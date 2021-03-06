/*
 * CCNx Android Services
 *
 * Copyright (C) 2010-2012 Palo Alto Research Center, Inc.
 *
 * This work is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation.
 * This work is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */

package org.ccnx.android.services;

import org.ccnx.android.ccnlib.CCNxServiceControl;
import org.ccnx.android.ccnlib.CCNxServiceCallback;
import org.ccnx.android.ccnlib.CCNxServiceStatus.SERVICE_STATUS;
import org.ccnx.android.ccnlib.CcndWrapper.CCND_OPTIONS;
import org.ccnx.android.ccnlib.RepoWrapper.CCNR_OPTIONS;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.MenuItem;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Spinner;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Android UI for controlling CCNx services.
 */
public final class Controller extends Activity implements OnClickListener {
	public final static String TAG = "CCNx Service Controller";
	private Button mAllBtn;
	private ProgressDialog pd;
	
	private Context _ctx;
	
	private TextView tvCcndStatus;
	private TextView tvRepoStatus;
	private TextView deviceIPAddress = null;
	
	private CCNxServiceControl control;
	private String mReleaseVersion = "Unknown";
    private BroadcastReceiver mReceiver;

	// Create a handler to receive status updates
	private final Handler _handler = new Handler() {
		public void handleMessage(Message msg){
			SERVICE_STATUS st = SERVICE_STATUS.fromOrdinal(msg.what);
			Log.d(TAG,"Received new status from CCNx Services: " + st.name());
			// This is very very lazy.  Instead of checking what we got, we'll just
			// update the state and let that get our new status
			// Considering above comment, we should decide whether this is overly complex and implement a state machine
			// that can be rigorously tested for state transitions, and is adhered to in the UI status notifications
			if ((st == SERVICE_STATUS.START_ALL_DONE) || (st == SERVICE_STATUS.STOP_ALL_DONE)) {
				mAllBtn.setText(R.string.allStartButton);
				mAllBtn.setEnabled(true);
			} 

			if (st == SERVICE_STATUS.START_ALL_ERROR) {
				Toast.makeText(_ctx, "Unable to Start Services.  Reason:" + control.getErrorMessage(), 20).show();
				mAllBtn.setText(R.string.allStartButton_Error);
			} 
			// Update the UI after we receive a notification, otherwise we won't capture all state changes
			updateState();
		}
	};
	
	CCNxServiceCallback cb = new CCNxServiceCallback() {
		public void newCCNxStatus(SERVICE_STATUS st) {
			_handler.sendEmptyMessage(st.ordinal());
		}
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.controllermain);   
        
        Log.d(TAG,"Creating Service Controller");
        
        _ctx = this.getApplicationContext();
        
       	initUI();
        init();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	// We should be saving out the state here for the UI so we don't lose user settings
        this.unregisterReceiver(mReceiver);
    }

    @Override
    public void onDestroy() {
    	control.disconnect();
    	super.onDestroy();
    }
    
    @Override
    public void onResume() {
    	super.onResume();

        IntentFilter intentfilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received android.net.conn.CONNECTIVITY_CHANGE");
                updateIPAddress();
            }
        };
        this.registerReceiver(mReceiver, intentfilter);
        //
        // Update on resume, as frequently, in old Android esp, WIFI gets
        // shut off and may lose the address it had
        //
        updateIPAddress();

        // We should updateState on resuming, in case Service state has changed
        updateState();
    }
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.servicemenu, menu);
    	return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	        case R.id.reset:
	        	// Need to figure out if this is always safe to call even when nothing is running
	            control.stopAll();
	            control.clearErrorMessage();
	            Toast.makeText(this, "Reset CCNxServiceStatus complete, new status is: {ccnd: " + control.getCcndStatus().name() + 
	            	", repo: " + control.getRepoStatus().name() + "}", 10).show();
	            return true;
	        case R.id.about:
	        	setContentView(R.layout.aboutview);
	        	TextView aboutdata = (TextView) findViewById(R.id.about_text);
	        	aboutdata.setText(mReleaseVersion + "\n" + aboutdata.getText());

	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
    private void init(){
    	control = new CCNxServiceControl(this);
    	control.registerCallback(cb);
    	control.connect();
    	try {
    		PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			mReleaseVersion = TAG + " " + pInfo.versionName;
		} catch(NameNotFoundException e) {
			Log.e(TAG, "Could not find package name.  Reason: " + e.getMessage());
		}
    	updateState();
    }

	public void onClick(View v) {
		switch( v.getId() ) {
		case R.id.allStartButton:
			allButton();
			break;
		default:
			Log.e(TAG, "Clicked unknown view");
		}
	}

	private void updateState(){
		if(control.isAllRunning()){
			mAllBtn.setText(R.string.allStopButton);
            mAllBtn.setEnabled(true);
            Log.d(TAG, "Repo and CCND are running, enable button");
		} else if (((control.getCcndStatus() == SERVICE_STATUS.SERVICE_OFF) && (control.getRepoStatus() == SERVICE_STATUS.SERVICE_OFF)) ||
			((control.getCcndStatus() == SERVICE_STATUS.SERVICE_FINISHED) && (control.getRepoStatus() == SERVICE_STATUS.SERVICE_FINISHED))
		) {
			Log.d(TAG, "Repo and CCND are both finished/off");
		} else {
			// We've potentially got to wait longer, or we've got problems
			// If we've got problems, report it via notifcation to taskbar
			if ((control.getCcndStatus() == SERVICE_STATUS.SERVICE_ERROR) || (control.getRepoStatus() == SERVICE_STATUS.SERVICE_ERROR)) {
				Log.e(TAG, "Error in CCNxServiceStatus.  Need to clear error and reset state");
				// Toast it for now
				Toast.makeText(this, "Error in CCNxServiceStatus.  Need to clear error and reset state", 20).show();
			}
		}
		tvCcndStatus.setText(control.getCcndStatus().name());
		tvRepoStatus.setText(control.getRepoStatus().name());
	}
	
	/**
	 * Start all services in the background
	 */
	private void allButton(){
        // Always disable the button after a click until we
        // reach a stable state, or hit error condition
        mAllBtn.setEnabled(false);
        mAllBtn.setText(R.string.allStartButton_Processing);

        Log.d(TAG, "Disabling All Button");
		if(control.isAllRunning()){
			// Everything is ready, we must stop
			control.stopAll();
		} else { /* Note, this doesn't take into account partially running state */
			// Not all running... attempt to start them
			// but first, get the user settings
			// Consider these to be our defaults
			// We don't really check validity of the data in terms of constraints
			// so we should shore this up to be more robust
			final EditText ccnrDir = (EditText) findViewById(R.id.key_ccnr_directory);  
			String val = ccnrDir.getText().toString();  
			if (isValid(val)) {
				control.setCcnrOption(CCNR_OPTIONS.CCNR_DIRECTORY, val);
			} else {
				// Toast it, and return (so the user fixes the bum field)
				Toast.makeText(this, "CCNR_DIRECTORY field is not valid.  Please set and then start.", 10).show();
				return;
			}
			
			final EditText ccnrGlobalPrefix= (EditText) findViewById(R.id.key_ccnr_global_prefix);  
			val = ccnrGlobalPrefix.getText().toString();  
			if (isValid(val)) {
				control.setCcnrOption(CCNR_OPTIONS.CCNR_GLOBAL_PREFIX, val);
			} else {
				// Toast it, and return (so the user fixes the bum field)
				Toast.makeText(this, "CCNR_GLOBAL_PREFIX field is not valid.  Please set and then start.", 10).show();
				return;
			}
			
			final Spinner ccnrDebugSpinner = (Spinner) findViewById(R.id.key_ccnr_debug);  
			val = ccnrDebugSpinner.getSelectedItem().toString();  
			if (isValid(val)) {
				control.setCcnrOption(CCNR_OPTIONS.CCNR_DEBUG, val);
			} else {
				// Toast it, and return (so the user fixes the bum field)
				// XXX I Don't think this will ever happen
				Toast.makeText(this, "CCNR_DEBUG field is not valid.  Please set and then start.", 10).show();
				return;
			}
			control.startAllInBackground();
		}
		// updateState();
	}

	private void initUI() {
		mAllBtn = (Button)findViewById(R.id.allStartButton);
        mAllBtn.setOnClickListener(this);

        tvCcndStatus = (TextView)findViewById(R.id.tvCcndStatus);
        tvRepoStatus = (TextView)findViewById(R.id.tvRepoStatus);
        deviceIPAddress = (TextView)findViewById(R.id.deviceIPAddress);
        String ipaddr = getIPAddress();
        
        if (ipaddr != null) {
        	deviceIPAddress.setText(ipaddr);
        } else {
        	deviceIPAddress.setText("Unable to determine IP Address");
        }
	}
	private String getIPAddress() {
		try {
			for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
				NetworkInterface nic = e.nextElement();
                Log.d(TAG,"---------------------------------");
                Log.d(TAG,"NIC: " + nic.toString());
                Log.d(TAG,"---------------------------------");
				for (Enumeration<InetAddress> enumIpAddr = nic.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress addr = enumIpAddr.nextElement();
                    if(addr != null)
                    {
                        Log.d(TAG, "      HostName: " + addr.getHostName());
                        Log.d(TAG, "         Class: " + addr.getClass().getSimpleName());
                        Log.d(TAG, "            IP: " + addr.getHostAddress());
                        Log.d(TAG, " CanonicalHost: " + addr.getCanonicalHostName());
                        Log.d(TAG, " Is SiteLocal?: " + addr.isSiteLocalAddress());
                    }
					if (!addr.isLoopbackAddress()) {
						return addr.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ex) {
			Toast.makeText(this, "Error obtaining IP Address.  Reason: " + ex.getMessage(), 10).show();
			// If we can't get our IP, we got problems
			// Report it
			Log.e(TAG, "Error obtaining IP Address.  Reason: " + ex.getMessage());
		}
		return null;
	}

	private boolean isValid(String val) {
		// Normally we'd do real field validation to make sure input matches type of input
		return (!((val == null) || (val.length() == 0)));
	}

    private void updateIPAddress() {
        String ipaddr = getIPAddress();

        if (ipaddr != null) {
            deviceIPAddress.setText(ipaddr);
        } else {
            deviceIPAddress.setText("Unable to determine IP Address");
        }
    }

	public void aboutviewButtonListener (View view) {
		// Called with user clicks OK, return to main view
		setContentView(R.layout.controllermain);
		initUI();
		updateState();
	}
}

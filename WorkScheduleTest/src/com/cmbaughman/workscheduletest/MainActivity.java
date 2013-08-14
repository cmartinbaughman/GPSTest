package com.cmbaughman.workscheduletest;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import android.content.IntentSender;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends FragmentActivity 
	implements LocationListener, GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener {
	
	private LocationRequest mLocationRequest;
	private LocationClient mLocationClient;

	// GUI Stuff
	private TextView mLatLng;
    private TextView mAddress;
    private ProgressBar mActivityIndicator;
    private TextView mConnectionState;
    private TextView mConnectionStatus;
    
    SharedPreferences mPrefs;
    SharedPreferences.Editor mEditor;
    
    // If updates are turned on. Note: get's set to true in handleRequestSuccess() for now. 
    boolean mUpdatesRequested = false;
	
    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mLatLng = (TextView) findViewById(R.id.lat_lng);
        mAddress = (TextView) findViewById(R.id.address);
        mActivityIndicator = (ProgressBar) findViewById(R.id.address_progress);
        mConnectionState = (TextView) findViewById(R.id.text_connection_state);
        mConnectionStatus = (TextView) findViewById(R.id.text_connection_status);
        
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(LocationUtils.UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setFastestInterval(LocationUtils.FAST_INTERVAL_CEILING_IN_MILLISECONDS);
        mUpdatesRequested = false;
        
        // Open SharedPreferences
        mPrefs = getSharedPreferences(LocationUtils.SHARED_PREFERENCES, Context.MODE_PRIVATE);
        mEditor = mPrefs.edit();
        
        // New LocationClient using the enclosing class to handle callbacks
        mLocationClient = new LocationClient(this, this, this);
        
	}
    
    @Override
    public void onStop() {
    	if(mLocationClient.isConnected()) {
    		//stopPeriodicUpdates
    	}
    	
    	mLocationClient.disconnect();
    	super.onStop();
    }
    
    @Override
    public void onPause() {
    	mEditor.putBoolean(LocationUtils.KEY_UPDATES_REQUESTED, mUpdatesRequested);
    	mEditor.commit();
    	
    	super.onPause();
    }
    
    @Override
    public void onStart() {
    	super.onStart();
    	mLocationClient.connect();
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	if(mPrefs.contains(LocationUtils.KEY_UPDATES_REQUESTED)) {
    		mUpdatesRequested = mPrefs.getBoolean(LocationUtils.KEY_UPDATES_REQUESTED, false);
    	}
    	else {
    		mEditor.putBoolean(LocationUtils.KEY_UPDATES_REQUESTED, false);
    		mEditor.commit();
    	}
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private boolean servicesConnected() {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		
		if(ConnectionResult.SUCCESS == resultCode) {
			Log.d(LocationUtils.APPTAG, getString(R.string.play_services_available));
			return true;
		}
		else {
			Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
			if (dialog != null) {
				ErrorDialogFragment errorFragment = new ErrorDialogFragment();
				errorFragment.setDialog(dialog);
				errorFragment.show(getSupportFragmentManager(), LocationUtils.APPTAG);
			}
			return false;
		}
		
	}
    
	public void getLocation(View view) {
		if(servicesConnected()) {
			Location curLocation = mLocationClient.getLastLocation();
			mLatLng.setText(LocationUtils.getLatLng(this, curLocation));
		}
	}
	
	 @SuppressLint("NewApi")
    public void getAddress(View v) {
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && !Geocoder.isPresent()) {
            // No geocoder is present. Issue an error message
            Toast.makeText(this, R.string.no_geocoder_available, Toast.LENGTH_LONG).show();
            return;
        }

        if (servicesConnected()) {

            // Get the current location
            Location currentLocation = mLocationClient.getLastLocation();

            // Turn the indefinite activity indicator on
            mActivityIndicator.setVisibility(View.VISIBLE);

            // Start the background task
            (new MainActivity.GetAddressTask(this)).execute(currentLocation);
        }
    }
	
	 public void startUpdates(View v) {
	        mUpdatesRequested = true;

	        if (servicesConnected()) {
	            startPeriodicUpdates();
	        }
	    }
	 
	 public void stopUpdates(View v) {
	        mUpdatesRequested = false;

	        if (servicesConnected()) {
	            stopPeriodicUpdates();
	        }
	    }

	 @Override
	 public void onConnected(Bundle bundle) {
		 mConnectionStatus.setText(R.string.connected);

	     if (mUpdatesRequested) {
	    	 startPeriodicUpdates();
	     }
	 }
	 
	@Override
    public void onDisconnected() {
        mConnectionStatus.setText(R.string.disconnected);
    }
	
	@Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {

                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

                /*
                * Thrown if Google Play services canceled the original
                * PendingIntent
                */

            } catch (IntentSender.SendIntentException e) {

                // Log the error
                e.printStackTrace();
            }
        } else {

            // If no resolution is available, display a dialog to the user with the error.
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    /**
     * Report location updates to the UI.
     *
     * @param location The updated location.
     */
    @Override
    public void onLocationChanged(Location location) {

        // Report to the UI that the location was updated
        mConnectionStatus.setText(R.string.location_updated);

        // In the UI, set the latitude and longitude to the value received
        mLatLng.setText(LocationUtils.getLatLng(this, location));
    }

    /**
     * In response to a request to start updates, send a request
     * to Location Services
     */
    private void startPeriodicUpdates() {

        mLocationClient.requestLocationUpdates(mLocationRequest, this);
        mConnectionState.setText(R.string.location_requested);
    }

    /**
     * In response to a request to stop updates, send a request to
     * Location Services
     */
    private void stopPeriodicUpdates() {
        mLocationClient.removeLocationUpdates(this);
        mConnectionState.setText(R.string.location_updates_stopped);
    }
    
    protected class GetAddressTask extends AsyncTask<Location, Void, String> {
    	Context localContext;
    	
    	public GetAddressTask(Context context) {
    		super();
    		localContext = context;
    	}
    	
		@Override
		protected String doInBackground(Location... params) {
			Geocoder geocoder = new Geocoder(localContext, Locale.getDefault());
			Location location = params[0];
			List<Address> addresses = null;
			
			try {
				addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
			}
			catch(IOException e1) {
				Log.e(LocationUtils.APPTAG, getString(R.string.IO_Exception_getFromLocation));
				e1.printStackTrace();
				return getString(R.string.IO_Exception_getFromLocation);
			}
			catch(IllegalArgumentException e2) {
				String errorString = getString(
                        R.string.illegal_argument_exception,
                        location.getLatitude(),
                        location.getLongitude()
                );
                // Log the error and print the stack trace
                Log.e(LocationUtils.APPTAG, errorString);
                e2.printStackTrace();
                return errorString;
			}
			
			if(addresses != null && addresses.size() > 0) {
				Address adr = addresses.get(0);
				
				String addressText = getString(R.string.address_output_string, 
						adr.getMaxAddressLineIndex() > 0 ?
							adr.getAddressLine(0) : "",
						adr.getLocality(),
						adr.getCountryName()
				);
				return addressText;
			}
			else {
				return getString(R.string.no_address_found);
			}
		}
		
		/**
	     * A method that's called once doInBackground() completes. Set the text of the
	     * UI element that displays the address. This method runs on the UI thread.
	     */
	    @Override
	    protected void onPostExecute(String address) {
	    		// Turn off the progress bar
	        	mActivityIndicator.setVisibility(View.GONE);

	        	// Set the address in the UI
	        	mAddress.setText(address);
	    }
    }

	private void showErrorDialog(int errorCode) {
	
	    // Get the error dialog from Google Play services
	    Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
	        errorCode,
	        this,
	        LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);
	
	    // If Google Play services can provide an error dialog
	    if (errorDialog != null) {
	
	        // Create a new DialogFragment in which to show the error dialog
	        ErrorDialogFragment errorFragment = new ErrorDialogFragment();
	
	        // Set the dialog in the DialogFragment
	        errorFragment.setDialog(errorDialog);
	
	        // Show the error dialog in the DialogFragment
	        errorFragment.show(getSupportFragmentManager(), LocationUtils.APPTAG);
	    }
	}

	public static class ErrorDialogFragment extends DialogFragment {
		
		private Dialog mDialog;
				
		public ErrorDialogFragment() {
			super();
			mDialog = null;
		}
		
		public void setDialog(Dialog dialog) {
			mDialog = dialog;
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return mDialog;
		}
	}
	
	  @Override
	    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

	        // Choose what to do based on the request code
	        switch (requestCode) {

	            // If the request code matches the code sent in onConnectionFailed
	            case LocationUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST :

	                switch (resultCode) {
	                    // If Google Play services resolved the problem
	                    case Activity.RESULT_OK:

	                        // Log the result
	                        Log.d(LocationUtils.APPTAG, getString(R.string.resolved));

	                        // Display the result
	                        mConnectionState.setText(R.string.connected);
	                        mConnectionStatus.setText(R.string.resolved);
	                    break;

	                    // If any other result was returned by Google Play services
	                    default:
	                        // Log the result
	                        Log.d(LocationUtils.APPTAG, getString(R.string.no_resolution));

	                        // Display the result
	                        mConnectionState.setText(R.string.disconnected);
	                        mConnectionStatus.setText(R.string.no_resolution);

	                    break;
	                }

	            // If any other request code was received
	            default:
	               // Report that this Activity received an unknown requestCode
	               Log.d(LocationUtils.APPTAG,
	                       getString(R.string.unknown_activity_request_code, requestCode));

	               break;
	        }
	    }
}

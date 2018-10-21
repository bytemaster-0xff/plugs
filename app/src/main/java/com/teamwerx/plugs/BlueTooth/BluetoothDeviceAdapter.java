package com.teamwerx.plugs.BlueTooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


/*
 * To be used in a future version where we could potentially select from different sensors
 */
public class BluetoothDeviceAdapter extends ArrayAdapter<BluetoothDevice> {

    TextView name;
    TextView address;

    public BluetoothDeviceAdapter(Context context) {
        super(context, 0);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        BluetoothDevice device = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        RecyclerView.ViewHolder viewHolder; // view lookup cache stored in tag
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
        } else {

        }

        // Populate the data into the template view using the data object
        // Return the completed to render on screen
        return convertView;
    }

    public BluetoothDevice findByName(String deviceName) {
        for(int idx = 0; idx < getCount(); ++idx) {
            if(getItem(idx).getName().contentEquals(deviceName)) {
                return getItem(idx);
            }
        }

        return null;
    }
}
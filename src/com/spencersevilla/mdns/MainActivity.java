package com.spencersevilla.mdns;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckedTextView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.net.InetAddress;

public class MainActivity extends Activity {
 
	MultiDNS mdns;

	ListView groupListView;
	ListView serviceListView;

	ArrayAdapter<DNSGroup> groupAdapter;
	ArrayAdapter<Service> serviceAdapter;
	
 	TabHost tabHost;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Initialize ContentView and tabs
	    setContentView(R.layout.activity_main);

		tabHost = (TabHost) findViewById(R.id.tabHost);
		tabHost.setup();

		TabSpec spec1 = tabHost.newTabSpec("tab1");
		spec1.setContent(R.id.tab1);
		spec1.setIndicator("DNS Groups");
		tabHost.addTab(spec1);

		TabSpec spec2 = tabHost.newTabSpec("tab2");
		spec2.setContent(R.id.tab2);
		spec2.setIndicator("Services Advertised");
		tabHost.addTab(spec2);
		
		// Start the main logic!
		mdns = new MultiDNS();
		
		// Hook up the UI
		initializeGroupList();
		initializeServiceList();
	}
	
	private void initializeGroupList() {
		
		groupListView = (ListView)findViewById(R.id.group_list);
		groupAdapter = new ArrayAdapter<DNSGroup>(this, 
				android.R.layout.simple_list_item_multiple_choice, mdns.allGroups);
		groupAdapter.setNotifyOnChange(true);
		
		groupListView.setAdapter(groupAdapter);
		groupListView.setItemsCanFocus(false);
		groupListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		groupListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
				CheckedTextView ctv = (CheckedTextView)arg1;
				DNSGroup dg = (DNSGroup) groupListView.getItemAtPosition(position);
				if (ctv.isChecked()) {
					mdns.leaveGroup(dg);
				} else {
					System.out.println("JOINING GROUP!!!");
					mdns.joinGroup(dg);
				}
				
				groupAdapter.notifyDataSetChanged();
			}
		});
	}

	private void initializeServiceList() {
		
		serviceListView = (ListView)findViewById(R.id.service_list);
		serviceAdapter = new ArrayAdapter<Service>(this, 
				android.R.layout.simple_list_item_multiple_choice, mdns.serviceList);
		serviceListView.setAdapter(serviceAdapter);
		serviceListView.setItemsCanFocus(false);
		serviceListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		serviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				CheckedTextView ctv = (CheckedTextView)arg1;
				System.out.println("hi!");
			}
		});
	}
	
	// when "Refresh List" button clicked
	public void refreshList(View view) {
		mdns.findOtherGroups();
		groupAdapter.notifyDataSetChanged();
	}

	// when "New AdHoc Group" button clicked
	public void newGroup(View view) {
		newGroupPopup();
	}
	
	public void newService(View view) {
		newServicePopup();
	}
	
	public void deleteService(View view) {
		System.out.println("hi!");
	}
	
	public void resolveService(View view) {
		InetAddress ia = mdns.resolveService("spencer.test.adhoc.spencer");
		if (ia != null) {
			System.out.println("success!");
		} else {
			System.out.println("failed!");
		}
	}

	private void newServicePopup() {
		AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
		helpBuilder.setTitle("Create Service");
		helpBuilder.setMessage("Enter the name of the service");
		
		final EditText input = new EditText(this);
		input.setSingleLine();
		input.setText("");
		helpBuilder.setView(input);
		
		helpBuilder.setPositiveButton("Create Service", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				String name = input.getText().toString();
				if (!name.equals("")) {
					mdns.createService(name);
					serviceAdapter.notifyDataSetChanged();
				}
			}
		});

		helpBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			// Do nothing
			}
		});

		// Remember, create doesn't show the dialog
		AlertDialog helpDialog = helpBuilder.create();
		helpDialog.show();
	}
	
	private void newGroupPopup() {
		AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
		helpBuilder.setTitle("Create Group");
		helpBuilder.setMessage("Enter the name of the group");
		
		final EditText input = new EditText(this);
		input.setSingleLine();
		input.setText("");
		helpBuilder.setView(input);
		
		helpBuilder.setPositiveButton("Create Group", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				String name = input.getText().toString();
				if (!name.equals("")) {
					mdns.createAdHocGroup(name);
					groupAdapter.notifyDataSetChanged();
					groupListView.setItemChecked(mdns.allGroups.size() - 1, true);
				}
			}
		});

		helpBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			// Do nothing
			}
		});

		// Remember, create doesn't show the dialog
		AlertDialog helpDialog = helpBuilder.create();
		helpDialog.show();
	}
}
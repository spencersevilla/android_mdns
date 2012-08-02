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
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity {
 
	MultiDNS mdns;

	ListView groupListView;
	ListView otherGroupsListView;
	ListView serviceListView;

	ArrayAdapter<DNSGroup> groupAdapter;
	ArrayAdapter<DNSGroup> otherGroupsAdapter;
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
		initializeOtherGroupsList();
		initializeServiceList();
	}
	
	private void initializeGroupList() {
		groupListView = (ListView)findViewById(R.id.group_list);
		groupAdapter = new ArrayAdapter<DNSGroup>(this, 
				android.R.layout.simple_list_item_multiple_choice, mdns.groupList);
		groupAdapter.setNotifyOnChange(true);
		
		groupListView.setAdapter(groupAdapter);
		groupListView.setItemsCanFocus(false);
		groupListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		for (int i = 0; i < mdns.groupList.size()) {
			groupListView.setItemChecked(i, true);
		}

		groupListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				CheckedTextView ctv = (CheckedTextView)arg1;
				System.out.println("hi!");
			}
		});
	}
	
	private void initializeOtherGroupsList() {
		
		otherGroupsListView = (ListView)findViewById(R.id.other_groups_list);
		otherGroupsAdapter = new ArrayAdapter<DNSGroup>(this, 
				android.R.layout.simple_list_item_multiple_choice, mdns.otherGroups);
		otherGroupsAdapter.setNotifyOnChange(true);
		
		otherGroupsListView.setAdapter(otherGroupsAdapter);
		otherGroupsListView.setItemsCanFocus(false);
		otherGroupsListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		otherGroupsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				CheckedTextView ctv = (CheckedTextView)arg1;
				System.out.println("hi!");
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
		// there's no way this command will result in us joining or leaving groups
		// so we only have to call this for the otherGroupsAdapter
		otherGroupsAdapter.notifyDataSetChanged();
	}

	// when "New AdHoc Group" button clicked
	public void newGroup(View view) {
		System.out.println("hi!");
	}
}
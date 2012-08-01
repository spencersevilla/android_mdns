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
 
	ListView groupListView;
	ListView serviceListView;
	
	ArrayList<String> groupList;
	ArrayAdapter<String> groupAdapter;
	
	ArrayList<String> serviceList;
	
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
		
		initializeGroupList();
		initializeServiceList();
	}
	
	private void initializeGroupList() {
		groupList = new ArrayList<String>(Arrays.asList(
				"ucsc.global", "parc.global"));
		
		groupListView = (ListView)findViewById(R.id.group_list);
		groupAdapter = new ArrayAdapter<String>(this, 
				android.R.layout.simple_list_item_multiple_choice, groupList);
		groupAdapter.setNotifyOnChange(true);
		
		groupListView.setAdapter(groupAdapter);
		groupListView.setItemsCanFocus(false);
		groupListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		groupListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				CheckedTextView ctv = (CheckedTextView)arg1;
				System.out.println("hi!");
			}
		});
	}
	
	private void initializeServiceList() {
		serviceList = new ArrayList<String>(Arrays.asList(
				"spencer", "printer"));
		
		serviceListView = (ListView)findViewById(R.id.service_list);
		ArrayAdapter<String> serviceAdapter = new ArrayAdapter<String>(this, 
				android.R.layout.simple_list_item_multiple_choice, serviceList);
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
		groupList.add("test1.test2.global");
		groupAdapter.notifyDataSetChanged();
	}

	// when "New AdHoc Group" button clicked
	public void newGroup(View view) {
		System.out.println("hi!");
	}
}
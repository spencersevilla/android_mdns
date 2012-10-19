package com.spencersevilla.android;
import com.spencersevilla.mdns.*;

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
import java.util.StringTokenizer;

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
		
		// IPTables command (must be rooted):
		// redirect everything in from UDP53 to UDP5300
		// this way the app is non-root and we can respond!
		String[] command = new String[] { "su", "-c", 
			"iptables -A PREROUTING -p 17 --dport 53 -j REDIRECT --to-port 5300"
		};

		System.setProperty("de.uniba.wiai.lspi.chord.data.ID.number.of.displayed.bytes", "4");
		System.setProperty("de.uniba.wiai.lspi.chord.data.ID.displayed.representation", "2");
		System.setProperty("de.uniba.wiai.lspi.chord.service.impl.ChordImpl.successors", "2");
		System.setProperty("de.uniba.wiai.lspi.chord.service.impl.ChordImpl.AsyncThread.no", "10");
		System.setProperty("de.uniba.wiai.lspi.chord.service.impl.ChordImpl.StabilizeTask.start", "12");
		System.setProperty("de.uniba.wiai.lspi.chord.service.impl.ChordImpl.StabilizeTask.interval", "12");
		System.setProperty("de.uniba.wiai.lspi.chord.service.impl.ChordImpl.FixFingerTask.start", "0");
		System.setProperty("de.uniba.wiai.lspi.chord.service.impl.ChordImpl.FixFingerTask.interval", "12");
		System.setProperty("de.uniba.wiai.lspi.chord.service.impl.ChordImpl.CheckPredecessorTask.start", "6");
		System.setProperty("de.uniba.wiai.lspi.chord.service.impl.ChordImpl.CheckPredecessorTask.interval", "12");
		System.setProperty("de.uniba.wiai.lspi.chord.com.socket.InvocationThread.corepoolsize", "10");
		System.setProperty("de.uniba.wiai.lspi.chord.com.socket.InvocationThread.maxpoolsize", "50");
		System.setProperty("de.uniba.wiai.lspi.chord.com.socket.InvocationThread.keepalivetime", "20");

        try {
          Process process = Runtime.getRuntime().exec(command);
          process.waitFor();
        } catch (Exception e) {
          e.printStackTrace();
        }

		// Start the main logic!
		try {
			mdns = new MultiDNS();
			mdns.start();
		} catch (Exception e) {
			System.out.println("error: could not create mdns");
			e.printStackTrace();
			System.exit(0);
		}

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

	// when "enter group_string here!" button clicked
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
		resolveServicePopup();
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
		helpBuilder.setTitle("Create/Join Group");
		helpBuilder.setMessage("Group descriptor string here!");
		
		final EditText input = new EditText(this);
		input.setSingleLine();
		input.setText("TOP 1 chord.test_top join 10.42.0.50 5301 5301");
		helpBuilder.setView(input);
		
		helpBuilder.setPositiveButton("Create/Join Group", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				String name = input.getText().toString();
				if (name.equals("")) {
					return;
				}

				StringTokenizer st = new StringTokenizer(name);

        		String command = st.nextToken();
        		if (command.equals("TOP")) {
        			int gid = Integer.parseInt(st.nextToken());
        			ArrayList<String> args = new ArrayList<String>();
        			while (st.hasMoreTokens()) {
        				args.add(st.nextToken());
        			}

        			DNSGroup group = mdns.createGroup(gid, args);

        			if (group == null) {
        				return;
        			}
        			mdns.joinGroup(group);

        		} else if (command.equals("SUB")) {
        			String parent_name = st.nextToken();

        			// lookup DNSGroup here
        			DNSGroup parent = mdns.findGroupByName(parent_name);

        			if (parent == null) {
        				return;
        			}

        			int gid = Integer.parseInt(st.nextToken());
        			ArrayList<String> args = new ArrayList<String>();
        			while (st.hasMoreTokens()) {
        				args.add(st.nextToken());
        			}

        			DNSGroup group = mdns.createSubGroup(parent, gid, args);

        			if (group == null) {
        				return;
        			}
        			mdns.joinGroup(group);
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
	
	private void resolveServicePopup() {
		AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
		helpBuilder.setTitle("Resolve Service");
		helpBuilder.setMessage("Enter the name of the service");
		
		final EditText input = new EditText(this);
		input.setSingleLine();
		input.setText("");
		helpBuilder.setView(input);
		
		helpBuilder.setPositiveButton("Resolve Service", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				String name = input.getText().toString();
				if (!name.equals("")) {
					InetAddress addr = mdns.resolveService(name);
					if (addr == null) {
						couldNotResolve(name);
					} else {
						resolvedService(name, addr.getHostAddress());
					}
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
	
	private void resolvedService(String service, String addr) {
		AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
		helpBuilder.setTitle("Resolve Service");
		helpBuilder.setMessage("Request for " + service + " returned " + addr);
				
		helpBuilder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			}
		});
		// Remember, create doesn't show the dialog
		AlertDialog helpDialog = helpBuilder.create();
		helpDialog.show();
	}

	private void couldNotResolve(String service) {
		AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
		helpBuilder.setTitle("Resolve Service");
		helpBuilder.setMessage("Request for " + service + " failed.");
				
		helpBuilder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			}
		});
		// Remember, create doesn't show the dialog
		AlertDialog helpDialog = helpBuilder.create();
		helpDialog.show();
	}
}
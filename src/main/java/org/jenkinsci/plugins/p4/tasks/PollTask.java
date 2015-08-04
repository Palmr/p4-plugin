package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.filters.Filter;
import org.jenkinsci.plugins.p4.filters.FilterPathImpl;
import org.jenkinsci.plugins.p4.filters.FilterPerChangeImpl;
import org.jenkinsci.plugins.p4.filters.FilterUserImpl;

import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.AccessException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.Changelist;

public class PollTask extends AbstractTask implements
		FileCallable<List<Integer>>, Serializable {

	private static final long serialVersionUID = 1L;

	private final List<Filter> filter;
	private final boolean perChange;

	private String pin;

	public PollTask(List<Filter> filter) {
		this.filter = filter;

		// look for incremental filter option
		boolean incremental = false;
		if (filter != null) {
			for (Filter f : filter) {
				if (f instanceof FilterPerChangeImpl) {
					if (((FilterPerChangeImpl) f).isPerChange()) {
						incremental = true;
					}
				}
			}
		}
		this.perChange = incremental;
	}

	@SuppressWarnings("unchecked")
	public List<Integer> invoke(File workspace, VirtualChannel channel)
			throws IOException {
		return (List<Integer>) tryTask();
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		List<Integer> changes = new ArrayList<Integer>();

		// find changes...
		if (pin != null && !pin.isEmpty()) {
			List<Integer> have = p4.listHaveChanges(pin);
			int last = 0;
			if (!have.isEmpty()) {
				last = have.get(have.size() - 1);
			}
			p4.log("P4: Polling with label/change: " + last + "," + pin);
			changes = p4.listChanges(last, pin);
		} else {
			List<Integer> have = p4.listHaveChanges();
			int last = 0;
			if (!have.isEmpty()) {
				last = have.get(have.size() - 1);
			}
			p4.log("P4: Polling with label/change: " + last + ",now");
			changes = p4.listChanges(last);
		}

		// filter changes...
		List<Integer> remainder = new ArrayList<Integer>();
		for (int c : changes) {
			Changelist changelist = p4.getChange(c);
			// add unfiltered changes to remainder list
			if (!filterChange(changelist, filter)) {
				remainder.add(changelist.getId());
				p4.log("... found change: " + changelist.getId());
			}
		}
		changes = remainder;

		// if build per change...
		if (!changes.isEmpty() && perChange) {
			int lowest = changes.get(changes.size() - 1);
			changes = Arrays.asList(lowest);
			p4.log("next change: " + lowest);
		}
		return changes;
	}

	public void setLimit(String expandedPin) {
		pin = expandedPin;
	}

	/**
	 * Returns true if change should be filtered
	 *
	 * @param changelist
	 * @return
	 * @throws AccessException
	 * @throws RequestException
	 * @throws Exception
	 */
	private boolean filterChange(Changelist changelist, List<Filter> scmFilter)
			throws Exception {
		// exit early if no filters
		if (scmFilter == null) {
			return false;
		}

		String user = changelist.getUsername();
		List<IFileSpec> files = changelist.getFiles(true);

		for (Filter f : scmFilter) {
			// Scan through User filters
			if (f instanceof FilterUserImpl) {
				// return is user matches filter
				String u = ((FilterUserImpl) f).getUser();
				if (u.equalsIgnoreCase(user)) {
					return true;
				}
			}

			// Scan through Path filters
			if (f instanceof FilterPathImpl) {
				// add unmatched files to remainder list
				List<IFileSpec> remainder = new ArrayList<IFileSpec>();
				String path = ((FilterPathImpl) f).getPath();
				for (IFileSpec s : files) {
					String p = s.getDepotPathString();
					if (!p.startsWith(path)) {
						remainder.add(s);
					}
				}

				// update files with remainder
				files = remainder;

				// add if all files are removed then remove change
				if (files.isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}
}

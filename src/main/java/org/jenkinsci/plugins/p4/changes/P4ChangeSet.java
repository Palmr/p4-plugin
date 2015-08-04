package org.jenkinsci.plugins.p4.changes;

import hudson.model.Run;
import hudson.scm.RepositoryBrowser;
import hudson.scm.ChangeLogSet;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;

public class P4ChangeSet extends ChangeLogSet<P4ChangeEntry> {

	private List<P4ChangeEntry> history;

	protected P4ChangeSet(Run<?, ?> run, RepositoryBrowser<?> browser,
			List<P4ChangeEntry> logs) {
		super(run, browser);
		this.history = Collections.unmodifiableList(logs);
	}

	public Iterator<P4ChangeEntry> iterator() {
		return history.iterator();
	}

	@Override
	public boolean isEmptySet() {
		return history.isEmpty();
	}

	public List<P4ChangeEntry> getHistory() {
		return history;
	}

	public Collection<P4ChangeEntry> getLogs() {
		return history;
	}

	public static void store(File file, List<Object> changes) {
		try {
			FileOutputStream o = new FileOutputStream(file);
			BufferedOutputStream b = new BufferedOutputStream(o);
			Charset c = Charset.forName("UTF-8");
			OutputStreamWriter w = new OutputStreamWriter(b, c);
			WriterOutputStream s = new WriterOutputStream(w);
			PrintStream stream = new PrintStream(s);

			stream.println("<?xml version='1.0' encoding='UTF-8'?>");
			stream.println("<changelog>");
			for (Object change : changes) {
				stream.println("\t<entry>");
				if (change instanceof String) {
					stream.println("\t\t<label>" + change + "</label>");
				} else if (change instanceof P4ChangeEntry) {
					P4ChangeEntry cl = (P4ChangeEntry) change;
					stream.println("\t\t<changenumber><changeInfo>"
							+ cl.getId() + "</changeInfo>");
					stream.println("\t\t<clientId>" + cl.getClientId()
							+ "</clientId>");

					stream.println("\t\t<msg>"
							+ StringEscapeUtils.escapeXml(cl.getMsg())
							+ "</msg>");
					stream.println("\t\t<changeUser>"
							+ StringEscapeUtils.escapeXml(cl.getAuthor()
									.getDisplayName()) + "</changeUser>");

					stream.println("\t\t<changeTime>"
							+ StringEscapeUtils.escapeXml(cl.getChangeTime())
							+ "</changeTime>");

					stream.println("\t\t<shelved>" + cl.isShelved()
							+ "</shelved>");

					stream.println("\t\t<files>");
					for (IFileSpec filespec : cl.getFiles()) {
						FileAction action = filespec.getAction();
						int revision = filespec.getEndRevision();

						// URL encode depot path
						String depotPath = filespec.getDepotPathString();
						String safePath = URLEncoder.encode(depotPath, "UTF-8");

						stream.println("\t\t<file endRevision=\"" + revision
								+ "\" action=\"" + action.name()
								+ "\" depot=\"" + safePath + "\" />");
					}
					stream.println("\t\t</files>");

					stream.println("\t\t</changenumber>");

				} else {
					stream.println("\t\t<changenumber>" + change
							+ "</changenumber>");
				}
				stream.println("\t</entry>");
			}
			stream.println("</changelog>");
			stream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
}

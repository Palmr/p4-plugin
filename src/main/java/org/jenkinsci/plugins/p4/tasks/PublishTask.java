package org.jenkinsci.plugins.p4.tasks;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.publish.Publish;

public class PublishTask extends AbstractTask implements FileCallable<Boolean>,
		Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger
			.getLogger(PublishTask.class.getName());

	private final Publish publish;

	public PublishTask(Publish publish) {
		this.publish = publish;
	}

	public Boolean invoke(File workspace, VirtualChannel channel)
			throws IOException {
		return (Boolean) tryTask();
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		try {
			// Check connection (might be on remote slave)
			if (!checkConnection(p4)) {
				return false;
			}

			// Look for changes and add to change-list, then publish
			boolean open = p4.buildChange();
			if (open) {
				p4.publishChange(publish);
			}
		} catch (Exception e) {
			p4.log("(p4):stop:exception\n");
			String msg = "Unable to publish workspace: " + e;
			logger.warning(msg);
			throw e;
		}
		return true;
	}
}

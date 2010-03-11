/**
 * A CCNx library test.
 *
 * Copyright (C) 2008, 2009, 2010 Palo Alto Research Center, Inc.
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

package org.ccnx.ccn.test.profiles.security.access.group;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;

import junit.framework.Assert;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.config.SystemConfiguration;
import org.ccnx.ccn.config.UserConfiguration;
import org.ccnx.ccn.impl.CCNFlowControl.SaveType;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.io.CCNFileInputStream;
import org.ccnx.ccn.io.CCNInputStream;
import org.ccnx.ccn.io.CCNOutputStream;
import org.ccnx.ccn.io.RepositoryFileOutputStream;
import org.ccnx.ccn.io.content.Link;
import org.ccnx.ccn.profiles.namespace.NamespaceManager;
import org.ccnx.ccn.profiles.namespace.ParameterizedName;
import org.ccnx.ccn.profiles.security.access.AccessControlManager;
import org.ccnx.ccn.profiles.security.access.AccessControlPolicyMarker;
import org.ccnx.ccn.profiles.security.access.AccessDeniedException;
import org.ccnx.ccn.profiles.security.access.group.ACL;
import org.ccnx.ccn.profiles.security.access.group.GroupAccessControlManager;
import org.ccnx.ccn.profiles.security.access.group.GroupAccessControlProfile;
import org.ccnx.ccn.profiles.security.access.group.ACL.ACLOperation;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.utils.CreateUserData;
import org.junit.BeforeClass;
import org.junit.Test;

public class ACPerformanceTestRepo {

	static ContentName domainPrefix, userKeystore, userNamespace, groupNamespace;
	static String[] userNames = {"Alice", "Bob", "Carol"};
	static ContentName baseDirectory, nodeName;
	static CreateUserData cua;
	static int blockSize = 8096;
	static Random rnd;
	static final String fileName = "./src/org/ccnx/ccn/test/profiles/security/access/group/earth.jpg";
	static final int fileSize = 101783;
	static CCNHandle _AliceHandle;
	static GroupAccessControlManager _AliceACM;

	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Log.setDefaultLevel(Level.WARNING);
		rnd = new Random();
		
		domainPrefix = ContentName.fromNative(UserConfiguration.defaultNamespace(), "" + rnd.nextInt(10000));
		userNamespace = GroupAccessControlProfile.userNamespaceName(domainPrefix);
		userKeystore = ContentName.fromNative(userNamespace, "_keystore_");
		groupNamespace = GroupAccessControlProfile.groupNamespaceName(domainPrefix);
		cua = new CreateUserData(userKeystore, userNames, userNames.length, true, "password".toCharArray(), CCNHandle.open());
		cua.publishUserKeysToRepository(userNamespace);

		// The root ACL at domainPrefix has Alice as a manager
		ArrayList<Link> ACLcontents = new ArrayList<Link>();
		Link lk = new Link(ContentName.fromNative(userNamespace, userNames[0]), ACL.LABEL_MANAGER, null);
		ACLcontents.add(lk);
		ACL rootACL = new ACL(ACLcontents);
		
		// Set user and group storage locations as parameterized names
		ArrayList<ParameterizedName> parameterizedNames = new ArrayList<ParameterizedName>();
		ParameterizedName uName = new ParameterizedName("User", userNamespace, null);
		parameterizedNames.add(uName);
		ParameterizedName gName = new ParameterizedName("Group", groupNamespace, null);
		parameterizedNames.add(gName);
		
		// Set access control policy marker	
		ContentName profileName = ContentName.fromNative(GroupAccessControlManager.PROFILE_NAME_STRING);
		AccessControlPolicyMarker.create(domainPrefix, profileName, rootACL, parameterizedNames, null, SaveType.REPOSITORY, CCNHandle.open());
		
		// get handle and ACM for Alice
		_AliceHandle = cua.getHandleForUser(userNames[0]);
		Assert.assertNotNull(_AliceHandle);
		NamespaceManager.clearSearchedPathCache();
		_AliceACM = (GroupAccessControlManager) AccessControlManager.findACM(domainPrefix, _AliceHandle);
		Assert.assertNotNull(_AliceACM);
		System.out.println("ns: " + _AliceACM.getNamespaceRoot());
	}
	
	@Test
	public void performanceTest() {
		createBaseDirectoryACL();
		writeFileInDirectory();

		// Alice and Bob have permission to read the file
		try {
			readFileAs(userNames[0]);
			readFileAs(userNames[1]);
		} catch (AccessDeniedException ade) {
			Assert.fail();
		}

		// Carol does not have permission to read the file
		try {
			readFileAs(userNames[2]);
			Assert.fail();
		} 
		catch (AccessDeniedException ade) {}

		updateACL();
		
		// Carol now has permission to read the file
		try {
			readFileAs(userNames[2]);
		}
		catch (AccessDeniedException ade) {
			Assert.fail();
		}
	}
	
	/**
	 * Create a new ACL at baseDirectory with Alice as a manager and Bob as a reader
	 */
	public void createBaseDirectoryACL() {
		long startTime = System.currentTimeMillis();

		try {
			baseDirectory = domainPrefix.append(ContentName.fromNative("/Alice" + rnd.nextInt(100000) + "/documents/images/"));
			System.out.println("Base directory: " + baseDirectory);
			ArrayList<Link> ACLcontents = new ArrayList<Link>();
			ACLcontents.add(new Link(ContentName.fromNative(userNamespace, userNames[0]), ACL.LABEL_MANAGER, null));
			ACLcontents.add(new Link(ContentName.fromNative(userNamespace, userNames[1]), ACL.LABEL_READER, null));		
			ACL baseDirACL = new ACL(ACLcontents);
			if (_AliceACM == null) System.out.println("Alice ACM is null");
			if (baseDirectory == null) System.out.println("base dir is null");
			if (baseDirACL == null) System.out.println("basedirACL is null");
			_AliceACM.setACL(baseDirectory, baseDirACL);
		}
		catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}

		System.out.println("createACL: " + (System.currentTimeMillis() - startTime));
	}
	
	/**
	 * write a file in the baseDirectory
	 */
	public void writeFileInDirectory() {
		long startTime = System.currentTimeMillis();
		
		try {
			InputStream is = new FileInputStream(fileName);
			nodeName = ContentName.fromNative(baseDirectory, "earth.jpg");
			CCNOutputStream ostream = new RepositoryFileOutputStream(nodeName, _AliceHandle);
			ostream.setTimeout(SystemConfiguration.MAX_TIMEOUT);
			
			int size = blockSize;
			int readLen = 0;
			byte [] buffer = new byte[blockSize];
			Log.finer("do_write: " + is.available() + " bytes left.");
			while ((readLen = is.read(buffer, 0, size)) != -1){	
				ostream.write(buffer, 0, readLen);
			}
			ostream.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}
		
		System.out.println("writeFile: " + (System.currentTimeMillis() - startTime));		
	}
	
	/**
	 * Read the file as the specified user
	 * @param userName the name of the user
	 * @throws AccessDeniedException
	 */
	public void readFileAs(String userName) throws AccessDeniedException {
		long startTime = System.currentTimeMillis();
		
		try {
			CCNHandle handle = cua.getHandleForUser(userName);
			CCNInputStream input = new CCNFileInputStream(nodeName, handle);
			input.setTimeout(SystemConfiguration.MAX_TIMEOUT);
			int readsize = 1024;
			byte [] buffer = new byte[readsize];
			int readcount = 0;
			int readtotal = 0;
			while ((readcount = input.read(buffer)) != -1){
				readtotal += readcount;
			}
			Assert.assertEquals(fileSize, readtotal);
		}
		// we want to propagate AccessDeniedException, but not IOException.
		// Since AccessDeniedException is a subclass of IOException, we catch and re-throw it.
		catch (AccessDeniedException ade) {
			System.out.println("Failed to read file as " + userName + ": " + (System.currentTimeMillis() - startTime));		
			throw ade;
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
			Assert.fail();
		}

		System.out.println("read file as " + userName + ": " + (System.currentTimeMillis() - startTime));		
	}
	
	/**
	 * Add Carol as a reader to the ACL on baseDirectory
	 */
	public void updateACL() {
		long startTime = System.currentTimeMillis();
		
		ArrayList<ACLOperation> ACLUpdates = new ArrayList<ACLOperation>();
		Link lk = new Link(ContentName.fromNative(userNamespace, userNames[2]));
		ACLUpdates.add(ACLOperation.addReaderOperation(lk));
		try {
			_AliceACM.updateACL(baseDirectory, ACLUpdates);
		} 
		catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}

		System.out.println("updateACL: " + (System.currentTimeMillis() - startTime));		
	}
	
}

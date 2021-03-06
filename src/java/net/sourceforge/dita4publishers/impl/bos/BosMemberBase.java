/**
 * Copyright (c) 2009 DITA2InDesign project (dita2indesign.sourceforge.net)  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at     http://www.apache.org/licenses/LICENSE-2.0  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. 
 */
package net.sourceforge.dita4publishers.impl.bos;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.dita4publishers.api.bos.BosException;
import net.sourceforge.dita4publishers.api.bos.BosMember;
import net.sourceforge.dita4publishers.api.bos.BosVisitor;
import net.sourceforge.dita4publishers.api.bos.BoundedObjectSet;
import net.sourceforge.dita4publishers.api.bos.DependencyType;
import net.sourceforge.dita4publishers.api.ditabos.DitaBoundedObjectSet;

import org.apache.commons.io.FilenameUtils;

/**
 * Base implementation for BOS members. Manages non-type-specific
 * properties and whatnot.
 */
public abstract class BosMemberBase implements BosMember {

	protected Map<String, BosMember> dependencies = new HashMap<String, BosMember>();
	protected File fileSystemDirectory;
	protected String fileName;
	protected File file = null;
	protected List<BosMember> children = new ArrayList<BosMember>();
	private List<BosMember> parents = new ArrayList<BosMember>();
	protected String key;
	protected BoundedObjectSet bos = null;
	protected boolean isXml = false;
	private boolean isInvalid = false;
	private URI sourceUri;
	private URI effectiveUri;
	private Map<String, Object> properties = new HashMap<String, Object>();
	private Map<DependencyType, Set<BosMember>> dependenciesByType = new HashMap<DependencyType, Set<BosMember>>();
	private Map<String, Set<DependencyType>> dependencyTypesByKey = new HashMap<String, Set<DependencyType>>();

	/**
	 * @param bos
	 */
	public BosMemberBase(BoundedObjectSet bos) {
		this.bos = bos;		
	}

	/**
	 * @param bos
	 * @param dataSourceUri
	 */
	public BosMemberBase(DitaBoundedObjectSet bos, URI dataSourceUri) {
		this.bos = bos;
		this.setDataSourceUri(dataSourceUri);
		this.setEffectiveUri(dataSourceUri);
		this.key = dataSourceUri.toString();
	}

	/**
	 * @param dataSourceUri
	 */
	public void setDataSourceUri(URI dataSourceUri) {
		this.sourceUri = dataSourceUri;
	}

	/**
	 * @param uri
	 */
	public void setEffectiveUri(URI uri) {
		this.effectiveUri = uri;
	}

	/**
	 * Registers a BOS member on which the member is dependent, specifing a key by 
	 * which the member can be later looked up, such as the original referencing
	 * element, the fully-qualified URI of the target, a database key, or whatever.
	 * Intended to enable mapping from original references in member data to
	 * the target managed object in order to rewrite pointers.
	 * @param key
	 * @param targetMember
	 */
	public void registerDependency(String key, BosMember targetMember) {
		registerDependency(key, targetMember, DependencyType.DEPENDENCY);
	}

	/**
	 * Registers a BOS member on which the member is dependent, specifing a key by 
	 * which the member can be later looked up, such as the original referencing
	 * element, the fully-qualified URI of the target, a database key, or whatever.
	 * Intended to enable mapping from original references in member data to
	 * the target managed object in order to rewrite pointers.
	 * <p>Note that while a given member is never added to dependencies multiple
	 * times, it may be registered under any number of dependency types.
	 * @param key
	 * @param targetMember
	 */
	public void registerDependency(String key, BosMember targetMember, DependencyType type) {
		dependencies.put(key, targetMember);
		registerDependencyByType(targetMember, type);
	}

	/**
	 * Allows registering child dependencies without adding the child to the dependencies map.
	 * @param targetMember
	 * @param type
	 */
	protected void registerDependencyByType(BosMember targetMember,
			DependencyType type) {
		if (!dependenciesByType.containsKey(type)) {
			this.dependenciesByType.put(type, new HashSet<BosMember>());
		}
		
		this.dependenciesByType.get(type).add(targetMember);

		if (!dependencyTypesByKey.containsKey(targetMember.getKey())) {
			this.dependencyTypesByKey.put(targetMember.getKey(), new HashSet<DependencyType>());
		}
		this.dependencyTypesByKey.get(targetMember.getKey()).add(type);
	}

	/**
	 * @return
	 */
	public String getFileName() {
		if (this.fileName == null && this.getEffectiveUri() != null) {
			this.fileName = FilenameUtils.getName(this.getEffectiveUri().getPath());
		}
		return this.fileName;
	}
	
	public File getFile() {
		return this.file;
	}
	
	public abstract void accept(BosVisitor visitor) throws Exception;

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public List<BosMember> getChildren() {
		return this.children;
	}

	public String getKey() {
		return this.key;
	}

	/**
	 * 
	 * @return The parent BOS member or null if the member is the root member
	 */
	public List<BosMember> getParents() {
		return this.parents;
	}

	public void addParent(BosMember parentMember) {
		if (parentMember != null && !this.parents.contains(parentMember)) {
			this.parents.add(parentMember);
		}
	}

	public void addChild(BosMember member) {
		if (!this.children.contains(member)) {
			this.children.add(member);
			member.addParent(this);
			registerDependencyByType(member, DependencyType.CHILD);
		}
		
	}

	public Map<String, ? extends BosMember> getDependencies() {
		return getDependencies(false);
	}
	
	public Map<String, ? extends BosMember> getDependencies(boolean includeChildren) {
		Map<String, BosMember> newMap = new HashMap<String, BosMember>();
		newMap.putAll(this.dependencies);
		if (includeChildren) {
			for (BosMember member : this.children) {
				newMap.put(member.getKey(), member);
			}
		}
		return newMap;
	}



	public BosMember getDependency(String key) {
		return this.dependencies.get(key);
	}
	
	public boolean isXml() {
		return this.isXml;
	}

	public InputStream getInputStream() throws BosException {
		try {
			if (this.sourceUri == null)
				throw new RuntimeException("sourceUri not set for BOS member " + this.toString());
			return this.sourceUri.toURL().openStream();
		} catch (MalformedURLException e) {
			throw new BosException(e);
		} catch (IOException e) {
			throw new BosException(e);
		}
	}

	@Override
	public String toString() {
			StringBuilder buf = new StringBuilder();
			buf.append("[")
			.append(this.getKey())
			.append("] ")
			.append(" ")
			.append(this.getClass().getSimpleName());
			
			if (this.getEffectiveUri() != null) {
				String fileName = this.getFileName();
				buf.append(", filename=")
				.append(fileName);
			} else {
				buf.append(", {No associated file}");
			}
			
			return buf.toString();
		}

	/**
	 * @return
	 */
	public URI getEffectiveUri() {
		return this.effectiveUri;
	}

	public URI getDataSourceUri() {
		return this.sourceUri;
	}

	public boolean isInvalid() {
		return this.isInvalid ;
	}
	
	public boolean equals(Object candMember) {
		return (this.getKey().equals(((BosMember)candMember).getKey()));
	}

	public Map<String, Object> getPropertyMap() {
		return this.properties;
	}

	public Object getPropertyValue(String key) {
		return this.properties.get(key);
	}

	public void setProperty(String key, Object value) {
		this.properties.put(key, value);		
	}

	public Set<DependencyType> getDependencyTypes() {
		return this.dependenciesByType.keySet();
	}

	public Set<BosMember> getDependenciesOfType(DependencyType type) {
		Set<BosMember> resultSet = this.dependenciesByType.get(type);
		return resultSet;
	}

	public Set<DependencyType> getDependencyTypes(String key) {
		Set<DependencyType> resultSet = this.dependencyTypesByKey .get(key);
		return resultSet;
	}

	public void setFileSystemDirectory(File directory) {
		this.fileSystemDirectory = directory;
	}

	/**
	 * @return
	 */
	public File getFileSystemDirectory() {
		return this.fileSystemDirectory;
	}
	
}
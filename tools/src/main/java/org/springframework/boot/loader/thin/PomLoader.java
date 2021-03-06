/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.loader.thin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.locator.DefaultModelLocator;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * Utility class to help with reading and extracting dependencies from a physical pom.
 * 
 * @author Dave Syer
 *
 */
public class PomLoader {

	private AetherEngine engine;

	public PomLoader(AetherEngine engine) {
		this.engine = engine;
	}

	public String getParent(Resource pom) {
		if (!pom.exists()) {
			return null;
		}
		Model model = readModel(pom);
		Parent parent = model.getParent();
		if (parent != null) {
			return parent.getGroupId() + ":" + parent.getArtifactId() + ":pom::"
					+ parent.getVersion();
		}
		return null;
	}

	public List<Dependency> getDependencies(Resource pom) {
		if (!pom.exists()) {
			return Collections.emptyList();
		}
		Model model = readModel(pom);
		replacePlaceholders(model);
		return convertDependencies(model.getDependencies());
	}

	public List<Dependency> getDependencyManagement(Resource pom) {
		if (!pom.exists()) {
			return Collections.emptyList();
		}
		List<Dependency> list = new ArrayList<>();
		Model model = readModel(pom);
		replacePlaceholders(model);
		if (model.getParent() != null) {
			list.add(new Dependency(getParentArtifact(model), "import"));
		}
		if (model.getDependencyManagement() != null) {
			list.addAll(convertDependencies(
					model.getDependencyManagement().getDependencies()));
		}
		return list;
	}

	private void replacePlaceholders(Model model) {
		PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}");

		Properties properties = new Properties();
		loadProperties(model, properties);
		for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
			String version = dependency.getVersion();
			if (version != null) {
				dependency.setVersion(helper.replacePlaceholders(version, properties));
			}
		}

		DependencyManagement dependencyManagement = model.getDependencyManagement();
		if (dependencyManagement != null) {

			for (org.apache.maven.model.Dependency dependency : dependencyManagement
					.getDependencies()) {
				String version = dependency.getVersion();
				if (version != null) {
					dependency
							.setVersion(helper.replacePlaceholders(version, properties));
				}
			}

		}

	}

	private void loadProperties(Model model, Properties properties) {
		if (model.getParent() != null) {
			Artifact artifact = getParentArtifact(model);
			try {
				List<File> resolved = engine.resolve(
						Arrays.asList(new Dependency(artifact, "import")), false);
				loadProperties(readModel(new FileSystemResource(resolved.get(0))),
						properties);
			}
			catch (ArtifactResolutionException e) {
				// TODO: log a warning?
			}
		}
		properties.putAll(model.getProperties());
		if (model.getVersion() != null) {
			properties.setProperty("project.version", model.getVersion());
		}
		else if (model.getParent().getVersion() != null) {
			properties.setProperty("project.version", model.getParent().getVersion());
		}
		if (model.getGroupId() != null) {
			properties.setProperty("project.groupId", model.getGroupId());
		}
		else if (model.getParent().getGroupId() != null) {
			properties.setProperty("project.groupId", model.getParent().getGroupId());
		}
		if (model.getArtifactId() != null) {
			properties.setProperty("project.artifactId", model.getArtifactId());
		}
	}

	private Artifact getParentArtifact(Model model) {
		Parent parent = model.getParent();
		return new DefaultArtifact(parent.getGroupId(), parent.getArtifactId(), "pom",
				parent.getVersion());
	}

	private List<Dependency> convertDependencies(
			List<org.apache.maven.model.Dependency> dependencies) {
		List<Dependency> result = new ArrayList<>();
		for (org.apache.maven.model.Dependency dependency : dependencies) {
			String scope = dependency.getScope();
			if (!"test".equals(scope) && !"provided".equals(scope)) {
				Dependency item = new Dependency(artifact(dependency),
						dependency.getScope());
				item = item.setExclusions(convertExclusions(dependency.getExclusions()));
				result.add(item);
			}
		}
		return result;
	}

	private Collection<Exclusion> convertExclusions(
			List<org.apache.maven.model.Exclusion> exclusions) {
		List<Exclusion> result = new ArrayList<>();
		for (org.apache.maven.model.Exclusion exclusion : exclusions) {
			result.add(new Exclusion(exclusion.getGroupId(), exclusion.getArtifactId(),
					"*", "*"));
		}
		return result;
	}

	private Artifact artifact(org.apache.maven.model.Dependency dependency) {
		return new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
				dependency.getClassifier(), dependency.getType(),
				dependency.getVersion());
	}

	private static Model readModel(Resource resource) {
		DefaultModelProcessor modelProcessor = new DefaultModelProcessor();
		modelProcessor.setModelLocator(new DefaultModelLocator());
		modelProcessor.setModelReader(new DefaultModelReader());

		try {
			return modelProcessor.read(resource.getInputStream(), null);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to build model from effective pom",
					ex);
		}
	}

}

/*
 * Copyright 2012-2016 the original author or authors.
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

package org.springframework.boot.experimental.gradle;

import java.io.File;
import java.util.Arrays;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.tasks.Jar;

/**
 * Gradle {@link Plugin} for Spring Boot's thin launcher.
 * <p>
 * If the Java plugin is applied to the project, a {@link GenerateLauncherPropertiesTask}
 * named {@code generateLibProperties} is added to the project. This task is configured to
 * use the {@code runtime} configuration to generate a {@code META-INF/thin.properties}
 * file in the {@code main} source set's resource output directory.
 *
 * @author Andy Wilkinson
 */
public class ThinLauncherPlugin implements Plugin<Project> {

	public void apply(final Project project) {
		project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {

			@Override
			public void execute(JavaPlugin plugin) {
				createLibPropertiesTask(project);
			}

		});
		project.getTasks().withType(Jar.class, new Action<Jar>() {

			@Override
			public void execute(Jar jar) {
				createCopyTask(project, jar);
				createResolveTask(project, jar);
			}

		});
	}

	private void createCopyTask(final Project project, final Jar jar) {
		final Copy copy = project.getTasks().create("thinResolvePrepare", Copy.class);
		copy.dependsOn("bootRepackage");
		copy.from(jar.getOutputs().getFiles());
		copy.into(new File(project.getBuildDir(), "thin/root"));
	}

	private void createResolveTask(final Project project, final Jar jar) {
		final Exec exec = project.getTasks().create("thinResolve", Exec.class);
		exec.dependsOn("thinResolvePrepare");
		exec.doFirst(new Action<Task>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(Task task) {
				Copy copy = (Copy) project.getTasks().getByName("thinResolvePrepare");
				exec.setWorkingDir(copy.getOutputs().getFiles().getSingleFile());
				exec.setCommandLine(Jvm.current().getJavaExecutable());
				exec.args(Arrays.asList("-Dthin.root=.", "-Dthin.dryrun", "-jar",
						jar.getArchiveName()));
			}
		});
	}

	private void createLibPropertiesTask(final Project project) {
		TaskContainer taskContainer = project.getTasks();
		taskContainer.create("generateLibProperties",
				GenerateLauncherPropertiesTask.class,
				new Action<GenerateLauncherPropertiesTask>() {

					@Override
					public void execute(
							GenerateLauncherPropertiesTask libPropertiesTask) {
						configureLibPropertiesTask(libPropertiesTask, project);
					}

				});

	}

	private void configureLibPropertiesTask(
			GenerateLauncherPropertiesTask libPropertiesTask, Project project) {
		libPropertiesTask.setConfiguration(project.getConfigurations()
				.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME));
		SourceSetContainer sourceSets = project.getConvention()
				.getPlugin(JavaPluginConvention.class).getSourceSets();
		File resourcesDir = sourceSets.getByName("main").getOutput().getResourcesDir();
		libPropertiesTask.setOutput(new File(resourcesDir, "META-INF/thin.properties"));
		project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME)
				.dependsOn(libPropertiesTask);
	}

}

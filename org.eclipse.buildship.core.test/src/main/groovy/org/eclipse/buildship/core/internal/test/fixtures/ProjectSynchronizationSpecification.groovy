/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.test.fixtures

import org.gradle.tooling.BuildAction
import org.gradle.tooling.ProjectConnection

import com.google.common.base.Optional
import com.google.common.base.Preconditions

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IWorkspaceRunnable
import org.eclipse.core.runtime.FileLocator
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.jobs.Job

import org.eclipse.buildship.core.BuildConfiguration
import org.eclipse.buildship.core.GradleBuild
import org.eclipse.buildship.core.GradleBuildConnectionTest
import org.eclipse.buildship.core.GradleCore
import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.SynchronizationResult
import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.workspace.CompositeModelQuery


abstract class ProjectSynchronizationSpecification extends WorkspaceSpecification {

    protected static final GradleDistribution DEFAULT_DISTRIBUTION = GradleDistribution.fromBuild()

    protected SynchronizationResult trySynchronizeAndWait(File location) {
        Optional<IProject> project = CorePlugin.workspaceOperations().findProjectByLocation(location.canonicalFile)
        Preconditions.checkState(project.present, "Workspace does not have project located at ${location.absolutePath}")
        trySynchronizeAndWait(project.get())
    }

    protected SynchronizationResult trySynchronizeAndWait(IProject project) {
        SynchronizationResult result = gradleBuildFor(project).synchronize(new NullProgressMonitor())
        waitForGradleJobsToFinish()
        waitForResourceChangeEvents()
        result
    }

    protected void synchronizeAndWait(File location) {
        SynchronizationResult result = trySynchronizeAndWait(location)
        assert result.status.isOK()
    }

    protected void synchronizeAndWait(IProject project) {
        SynchronizationResult result = trySynchronizeAndWait(project)
        assert result.status.isOK()
    }

    protected SynchronizationResult tryImportAndWait(File location, GradleDistribution gradleDistribution = GradleDistribution.fromBuild(), File javaHome = null) {
        GradleBuild gradleBuild = gradleBuildFor(location, gradleDistribution, javaHome)
        SynchronizationResult result = gradleBuild.synchronize(new NullProgressMonitor())
        waitForGradleJobsToFinish()
        waitForResourceChangeEvents()
        result
    }

    protected void importAndWait(File location, GradleDistribution gradleDistribution = GradleDistribution.fromBuild(), File javaHome = null) {
        SynchronizationResult result = tryImportAndWait(location, gradleDistribution, javaHome)
        if (!result.status.isOK()) {
            result.status.getException().printStackTrace();
        }
        assert result.status.isOK()
    }

    protected static GradleBuild gradleBuildFor(File location, GradleDistribution gradleDistribution = GradleDistribution.fromBuild(), File javaHome = null) {
        BuildConfiguration configuration = BuildConfiguration.forRootProjectDirectory(location)
            .gradleDistribution(gradleDistribution)
            .overrideWorkspaceConfiguration(true)
            .javaHome(javaHome)
            .build()
        GradleCore.workspace.createBuild(configuration)
    }

    protected static GradleBuild gradleBuildFor(IProject project) {
        GradleCore.workspace.getBuild(project).orElseThrow({ new RuntimeException("No Gradle build for $project") })
    }
    protected def waitForGradleJobsToFinish() {
        Job.jobManager.join(CorePlugin.GRADLE_JOB_FAMILY, null)
    }

    protected void waitForResourceChangeEvents() {
        workspace.run({} as IWorkspaceRunnable, null, IResource.NONE, null);
    }
}

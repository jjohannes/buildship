/*******************************************************************************
 * Copyright (c) 2019 Gradle Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.buildship.core.internal.workspace

import org.gradle.api.JavaVersion
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Unroll

import org.eclipse.buildship.core.GradleDistribution
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification

class ImportingProjectCrossVersionTest extends ProjectSynchronizationSpecification {

    File multiProjectDir
    File compositeProjectDir

    def setup() {
        multiProjectDir = dir('multi-project-build') {
            file 'settings.gradle', '''
                rootProject.name = 'root'
                include 'sub1'
                include 'sub2'
                include 'sub2:subSub1'
            '''

            file 'build.gradle', '''
                description = 'a sample root project'
                task myTask {}
            '''

            dir('sub1') {
                file 'build.gradle', '''
                    description = 'sub project 1'
                    task myFirstTaskOfSub1 {
                        description = '1st task of sub1'
                        group = 'build'
                    }
                   task mySecondTaskOfSub1 {
                       description = '2nd task of sub1'
                   }
                '''
            }
            dir('sub2') {
                file 'build.gradle', '''
                    description = 'sub project 2'
                    task myFirstTaskOfSub2 {
                        description = '1st task of sub2'
                    }
                    task mySecondTaskOfSub2 {
                        description = '2nd task of sub2'
                    }
                    task myTask {
                        description = 'another task of sub2'
                        group = 'build'
                    }

                '''

                dir('subSub1') {
                    file 'build.gradle', '''
                        description = 'subSub project 1 of sub project 2'
                        task myFirstTaskOfSub2subSub1{
                            description = '1st task of sub2:subSub1'
                        }
                        task mySecondTaskOfSub2subSub1{
                            description = '2nd task of sub2:subSub1'
                        }
                        task myTask {}
                    '''
                }
            }
        }

        compositeProjectDir = dir('composite-build') {
            file 'settings.gradle', '''
                rootProject.name='root'
                includeBuild 'included1'
                includeBuild 'included2'
            '''
            dir('included1') {
                dir('sub1')
                dir('sub2')
                file 'settings.gradle', '''
                    rootProject.name = 'included1'
                    include 'sub1', 'sub2'
                '''
            }
            dir('included2') {
                dir('sub1')
                dir('sub2')
                file 'settings.gradle', '''
                    rootProject.name = 'included2'
                    include 'sub1', 'sub2'
                '''
            }
        }
    }

    @Unroll
    def "Can import a multi-project build with Gradle #distribution.version"(GradleDistribution distribution) {
        when:
        importAndWait(multiProjectDir, distribution)

        then:
        allProjects().size() == 4
        numOfGradleErrorMarkers == 0
        findProject('root')
        findProject('sub1')
        findProject('sub2')
        findProject('subSub1')

        where:
        distribution << supportedGradleDistributions
    }

    @Unroll
    def "Included builds imported with de-duplicated names for #distribution.version"(GradleDistribution distribution) {
        when:
        importAndWait(compositeProjectDir, distribution)

        then:
        allProjects().size() == 7
        findProject('root')
        findProject('included1')
        findProject('included2')
        findProject('included1-sub1')
        findProject('included1-sub2')
        findProject('included2-sub1')
        findProject('included2-sub2')

        where:
        distribution << getSupportedGradleDistributions('>=5.5')
    }

	@Unroll
	def "Can handle duplicated build includes"(GradleDistribution distribution) {
		given:
		file('composite-build/included1/settings.gradle') << '''
			includeBuild '../included2'
        '''

		when:
		importAndWait(compositeProjectDir, distribution)

		then:
		allProjects().size() == 7
		findProject('root')
		findProject('included1')
		findProject('included2')
		findProject('included1-sub1')
		findProject('included1-sub2')
		findProject('included2-sub1')
		findProject('included2-sub2')

		where:
        distribution << getSupportedGradleDistributions('>=4.10')
	}

	@Unroll
	def "Can handle cyclic build includes"(GradleDistribution distribution) {
		given:
		file('composite-build/included1/settings.gradle') << '''
			includeBuild '../included2'
        '''
		file('composite-build/included2/settings.gradle') << '''
			includeBuild '../included1'
        '''

		when:
		importAndWait(compositeProjectDir, distribution)

		then:
		allProjects().size() == 7
		findProject('root')
		findProject('included1')
		findProject('included2')
		findProject('included1-sub1')
		findProject('included1-sub2')
		findProject('included2-sub1')
		findProject('included2-sub2')

		where:
		distribution << getSupportedGradleDistributions('>=6.8')
	}

    @Ignore("TODO Buildship doesn't de-duplicate project names which makes the synchronization fail")
    @Unroll
    def "Included builds imported but not de-duplicated names for #distribution.version"(GradleDistribution distribution) {
        when:
        importAndWait(compositeProjectDir, distribution)

        then:
        allProjects().size() == 7
        findProject('root')
        findProject('included1')
        findProject('included2')
        findProject('included1-sub1')
        findProject('included1-sub2')
        findProject('included2-sub1')
        findProject('included2-sub2')

        where:
        distribution << getSupportedGradleDistributions('<4.0 >=3.3')
    }

    @Unroll
    @IgnoreIf({ JavaVersion.current().isJava9Compatible() })
    def "Included builds ignored for #distribution.version"(GradleDistribution distribution) {
        when:
        importAndWait(compositeProjectDir, distribution)

        then:
        allProjects().size() == 1
        findProject('root')

        where:
        distribution << getSupportedGradleDistributions('<3.3 >=3.1')
    }
}


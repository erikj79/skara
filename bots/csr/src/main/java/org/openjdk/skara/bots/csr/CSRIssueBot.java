/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.skara.bots.csr;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.openjdk.skara.bot.Bot;
import org.openjdk.skara.bot.WorkItem;
import org.openjdk.skara.forge.HostedRepository;
import org.openjdk.skara.issuetracker.Issue;
import org.openjdk.skara.issuetracker.IssueProject;

/**
 * The CSRIssueBot polls an IssueProject for updated issues of CSR type. When
 * found, IssueWorkItems are created to figure out if any PR needs to be
 * re-evaluated.
 */
public class CSRIssueBot implements Bot {
    private final Logger log = Logger.getLogger("org.openjdk.skara.bots.csr");

    private final IssueProject issueProject;
    private final List<HostedRepository> repositories;
    // Keeps track of updatedAt timestamps from the previous call to getPeriodicItems,
    // so we can avoid re-evaluating issues that are returned again without any actual
    // update.
    private Map<String, ZonedDateTime> issueUpdatedAt = new HashMap<>();
    // The last found updatedAt from any issue.
    private ZonedDateTime lastUpdatedAt;

    public CSRIssueBot(IssueProject issueProject, List<HostedRepository> repositories) {
        this.issueProject = issueProject;
        this.repositories = repositories;
    }

    @Override
    public String toString() {
        return "CSRIssueBot@" + issueProject.name();
    }

    @Override
    public List<WorkItem> getPeriodicItems() {
        var ret = new ArrayList<WorkItem>();
        // In the very first round, we just find the last updated issue to
        // initialize lastUpdatedAt. There is no need for reacting to any CSR
        // issue update before that, as the CSRPullRequestBot will go through
        // every open PR at startup anyway.
        if (lastUpdatedAt == null) {
            var lastUpdatedIssue = issueProject.lastUpdatedIssue();
            if (lastUpdatedIssue.isPresent()) {
                Issue issue = lastUpdatedIssue.get();
                lastUpdatedAt = issue.updatedAt();
                issueUpdatedAt.put(issue.id(), issue.updatedAt());
                log.fine("Setting lastUpdatedAt from last updated issue " + issue.id() + " updated at " + lastUpdatedAt);
            } else {
                // If no previous issue was found, initiate lastUpdatedAt to something far
                // enough back so that we are guaranteed to find any new CSR issues going
                // forward.
                lastUpdatedAt = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
                log.warning("No CSR issue found, setting lastUpdatedAt to " + lastUpdatedAt);
            }
            return ret;
        }

        var newIssuesUpdatedAt = new HashMap<String, ZonedDateTime>();
        var issues = issueProject.csrIssues(lastUpdatedAt);
        for (var issue : issues) {
            newIssuesUpdatedAt.put(issue.id(), issue.updatedAt());
            // Update the lastUpdatedAt value with the highest found value for next call
            if (issue.updatedAt().isAfter(lastUpdatedAt)) {
                lastUpdatedAt = issue.updatedAt();
            }
            var lastUpdate = issueUpdatedAt.get(issue.id());
            if (lastUpdate != null) {
                if (!issue.updatedAt().isAfter(lastUpdate)) {
                    continue;
                }
            }
            var issueWorkItem = new IssueWorkItem(this, issue);
            log.fine("Scheduling: " + issueWorkItem);
            ret.add(issueWorkItem);
        }
        issueUpdatedAt = newIssuesUpdatedAt;
        return ret;
    }

    @Override
    public String name() {
        return CSRBotFactory.NAME;
    }

    List<HostedRepository> repositories() {
        return repositories;
    }
}

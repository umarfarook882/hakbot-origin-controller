/*
 * This file is part of Hakbot Origin Controller.
 *
 * Hakbot Origin Controller is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Hakbot Origin Controller is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Hakbot Origin Controller. If not, see http://www.gnu.org/licenses/.
 */
package io.hakbot.controller.workers;

import alpine.event.framework.Event;
import alpine.event.framework.EventService;
import alpine.event.framework.Subscriber;
import alpine.logging.Logger;
import io.hakbot.controller.event.JobProcessEvent;
import io.hakbot.controller.event.JobUpdateEvent;
import io.hakbot.controller.model.Job;
import io.hakbot.controller.model.SystemAccount;
import io.hakbot.controller.persistence.QueryManager;
import io.hakbot.providers.AsynchronousProvider;
import io.hakbot.providers.Provider;
import io.hakbot.providers.SynchronousProvider;
import java.lang.reflect.Constructor;

/**
 * The JobProcessWorker is a Subscriber, that when a JobProcessEvent is fired,
 * will begin to process the specified job. This class begins by initializing
 * a provider, checking if the provider is available to process jobs, and
 * submits a request to the provider to begin processing. This class supports
 * the processing of both AsynchronousProvider and SynchronousProvider jobs.
 *
 * @see JobProcessEvent
 */
public class JobProcessWorker implements Subscriber {

    private static final Logger LOGGER = Logger.getLogger(JobProcessWorker.class);

    public void inform(Event e) {
        if (e instanceof JobProcessEvent) {
            final JobProcessEvent event = (JobProcessEvent)e;

            final QueryManager qm = new QueryManager();
            final Job job = qm.getJob(event.getJobUuid(), new SystemAccount());
            qm.close();

            LOGGER.info("Job: " + event.getJobUuid() + " is being processed.");

            final boolean initialized, isAvailable;
            try {
                final ExpectedClassResolver resolver = new ExpectedClassResolver();
                final Class clazz = resolver.resolveProvider(job);
                @SuppressWarnings("unchecked")
                final Constructor<?> con = clazz.getConstructor();
                final Provider provider = (AsynchronousProvider.class.isAssignableFrom(clazz)) ?
                        (AsynchronousProvider) con.newInstance() : (SynchronousProvider) con.newInstance();

                initialized = provider.initialize(job);
                if (initialized) {
                    EventService.getInstance().publish(new JobUpdateEvent(job.getUuid()).message("Initialized " + provider.getName()));
                    isAvailable = provider.isAvailable(job);
                } else {
                    EventService.getInstance().publish(new JobUpdateEvent(job.getUuid()).state(State.FAILED).message("Unable to initialize " + provider.getName()));
                    return; // Cannot continue.
                }

                if (isAvailable) {
                    EventService.getInstance().publish(new JobUpdateEvent(job.getUuid()).state(State.IN_PROGRESS));
                    if (provider instanceof AsynchronousProvider) {
                        // Asynchronously process a job. Another task will periodically poll for updates and status.
                        ((AsynchronousProvider) provider).process(job);
                    } else {
                        // Synchronous execution needs to wait for the process to complete, thus holding up a thread.
                        // The boolean result from the execution determines if the execution was successful or not.
                        final boolean success = ((SynchronousProvider) provider).process(job);
                        if (success) {
                            EventService.getInstance().publish(new JobUpdateEvent(job.getUuid()).state(State.COMPLETED));
                        } else {
                            EventService.getInstance().publish(new JobUpdateEvent(job.getUuid()).state(State.FAILED));
                        }
                    }
                } else {
                    EventService.getInstance().publish(new JobUpdateEvent(job.getUuid()).state(State.UNAVAILABLE));
                }
            } catch (Throwable ex) {
                LOGGER.error(ex.getMessage());
                EventService.getInstance().publish(new JobUpdateEvent(job.getUuid()).state(State.FAILED).message(ex.getMessage()));
            }
        }
    }
}

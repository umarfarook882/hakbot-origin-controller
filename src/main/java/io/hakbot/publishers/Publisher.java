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
package io.hakbot.publishers;

import io.hakbot.controller.model.Job;
import io.hakbot.controller.plugin.Plugin;

public interface Publisher extends Plugin {

    /**
     * This method is called prior to any other method and is intended to initialize
     * the instance of the publisher.
     */
    boolean initialize(Job job);

    /**
     * Publishes the results from a job. Returns true if the publish was successful, false if not.
     */
    boolean publish(Job job);

}

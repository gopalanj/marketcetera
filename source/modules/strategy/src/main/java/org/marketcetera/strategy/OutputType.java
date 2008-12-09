package org.marketcetera.strategy;

import org.marketcetera.core.ClassVersion;

/**
 * Describes the types of data that a strategy can emit.
 *
 * @author <a href="mailto:colin@marketcetera.com">Colin DuPlantis</a>
 * @version $Id$
 * @since 1.0.0
 */
@ClassVersion("$Id$")
public enum OutputType
{
    /**
     * orders created by this strategy
     */
    ORDERS,
    /**
     * trade suggestions created by this strategy
     */
    SUGGESTIONS,
    /**
     * events created by this strategy
     */
    EVENTS,
    /**
     * all objects, regardless of type (includes all the above)
     */
    ALL
}
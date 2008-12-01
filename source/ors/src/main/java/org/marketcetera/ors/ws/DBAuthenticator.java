package org.marketcetera.ors.ws;

import org.marketcetera.ors.security.SingleSimpleUserQuery;
import org.marketcetera.persist.NoResultException;
import org.marketcetera.persist.PersistenceException;
import org.marketcetera.util.misc.ClassVersion;
import org.marketcetera.util.ws.stateful.Authenticator;
import org.marketcetera.util.ws.stateless.StatelessClientContext;

/**
 * A session authenticator that uses the database for authentication. 
 *
 * @author tlerios@marketcetera.com
 * @since $Release$
 * @version $Id$
 */

/* $License$ */

@ClassVersion("$Id$") //$NON-NLS-1$
public class DBAuthenticator
    implements Authenticator
{

    // Authenticator.

    @Override
    public boolean shouldAllow
        (StatelessClientContext context,
         String user,
         char[] password)
    {
        try {
            (new SingleSimpleUserQuery(user)).fetch().
                validatePassword(password);
        } catch (NoResultException ex) {
            Messages.BAD_CREDENTIALS.warn(this,ex,user);
            return false;
        } catch (PersistenceException ex) {
            Messages.BAD_CREDENTIALS.warn(this,ex,user);
            return false;
        }
        return true;
    }
}
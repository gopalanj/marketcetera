package org.marketcetera.photon;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.marketcetera.core.AccountID;
import org.marketcetera.core.ClassVersion;
import org.marketcetera.core.MSymbol;
import org.marketcetera.messagehistory.MessageHolder;

/**
 * The PhotonAdapterFactory produces adapters that the RCP uses to 
 * display Marketcetera model objects in the UI.  Instances of 
 * {@link IWorkbenchAdapter} returned by {@link #getAdapter(Object, Class)}
 * can provide the text to display as well as parent an child relationships
 * for tree-based GUI components.
 * 
 * @author gmiller
 *
 */
////////////////////////////////////////////////////////
// TODO: is any of the code in this class used anymore?
////////////////////////////////////////////////////////
@ClassVersion("$Id$")
public class PhotonAdapterFactory implements IAdapterFactory {


	protected IWorkbenchAdapter messageAdapter = new IWorkbenchAdapter() {
		public Object getParent(Object o) {
			return null;
		}

		public String getLabel(Object o) {
			return "Message";
		}

		public ImageDescriptor getImageDescriptor(Object object) {
			return null;
		}

		public Object[] getChildren(Object o) {
			return new Object[0];
		}
	};

	/**
	 * Returns an adapter of the specified class for the specified object.
	 * Currently the only valid values for adaptableObject are
	 * {@link MessageHolder}, {@link AccountID}, and {@link MSymbol}.
	 * Currently the only valid value of adapterType is
	 * {@link IWorkbenchAdapter}. All others will return null.
	 * 
	 * @param adaptableObject
	 *            the object for which to get an adapter
	 * @param adapterType
	 *            the class (or superclass or interface) of the adapter to get
	 * @return an adapter that is an instance of a subclass of adapterType, or
	 *         null if adaptableObject or adapterType are unsupported.
	 * @see org.eclipse.core.runtime.IAdapterFactory#getAdapter(java.lang.Object,
	 *      java.lang.Class)
	 */
	@SuppressWarnings("unchecked") // overrides unparameterized method
	public Object getAdapter(Object adaptableObject, Class adapterType) {
		if (adapterType == IWorkbenchAdapter.class
				&& adaptableObject instanceof MessageHolder)
			return messageAdapter;
		if (adapterType == IWorkbenchAdapter.class
				&& adaptableObject instanceof AccountID)
			return accountAdapter;
		if (adapterType == IWorkbenchAdapter.class
				&& adaptableObject instanceof MSymbol)
			return symbolAdapter;

		return null;
	}

	@SuppressWarnings("unchecked")  // overrides unparameterized method
	public Class[] getAdapterList() {
		return new Class[] { IWorkbenchAdapter.class };
	}

	// ///////////////////
	// Filter stuff

	private IWorkbenchAdapter accountAdapter = new IWorkbenchAdapter() {
		public Object getParent(Object o) {
			return null;
		}

		public String getLabel(Object o) {
			AccountID accountID = (AccountID) o;
			String nick = accountID.getAccountNickname();
			if (nick != null && !nick.equals("")) {
				nick = " (" + nick + ")";
			} else {
				nick = "";
			}
			return accountID.toString() + nick;
		}

		public ImageDescriptor getImageDescriptor(Object object) {
			return PhotonPlugin.getImageDescriptor(IImageKeys.ACCOUNT);
		}

		public Object[] getChildren(Object o) {
			return new Object[0];
		}
	};

	private IWorkbenchAdapter symbolAdapter = new IWorkbenchAdapter() {
		public Object getParent(Object o) {
			return null;
		}

		public String getLabel(Object o) {
			MSymbol aSymbol = (MSymbol) o;
			return aSymbol.toString();
		}

		public ImageDescriptor getImageDescriptor(Object object) {
			return PhotonPlugin.getImageDescriptor(IImageKeys.EQUITY);
		}

		public Object[] getChildren(Object o) {
			return new Object[0];
		}
	};



}
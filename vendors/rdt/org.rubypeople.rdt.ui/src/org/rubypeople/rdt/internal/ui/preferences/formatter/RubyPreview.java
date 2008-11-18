/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.rubypeople.rdt.internal.ui.preferences.formatter;

import java.util.Map;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.MarginPainter;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.rubypeople.rdt.core.formatter.DefaultCodeFormatterConstants;
import org.rubypeople.rdt.internal.ui.RubyPlugin;
import org.rubypeople.rdt.internal.ui.rubyeditor.RubySourceViewer;
import org.rubypeople.rdt.internal.ui.text.IRubyPartitions;
import org.rubypeople.rdt.internal.ui.text.SimpleRubySourceViewerConfiguration;
import org.rubypeople.rdt.ui.PreferenceConstants;
import org.rubypeople.rdt.ui.text.RubyTextTools;

public abstract class RubyPreview {

	private final class RubySourcePreviewerUpdater {

		final IPropertyChangeListener fontListener = new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty().equals(
						PreferenceConstants.EDITOR_TEXT_FONT)) {
					final Font font = JFaceResources
							.getFont(PreferenceConstants.EDITOR_TEXT_FONT);
					fSourceViewer.getTextWidget().setFont(font);
					if (fMarginPainter != null) {
						fMarginPainter.initialize();
					}
				}
			}
		};

		final IPropertyChangeListener propertyListener = new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (fViewerConfiguration.affectsTextPresentation(event)) {
					fViewerConfiguration.handlePropertyChangeEvent(event);
					fSourceViewer.invalidateTextPresentation();
				}
			}
		};

		public RubySourcePreviewerUpdater() {

			JFaceResources.getFontRegistry().addListener(fontListener);
			fPreferenceStore.addPropertyChangeListener(propertyListener);

			fSourceViewer.getTextWidget().addDisposeListener(
					new DisposeListener() {
						public void widgetDisposed(DisposeEvent e) {
							JFaceResources.getFontRegistry().removeListener(
									fontListener);
							fPreferenceStore
									.removePropertyChangeListener(propertyListener);
						}
					});
		}
	}

	protected final SimpleRubySourceViewerConfiguration fViewerConfiguration;

	protected final Document fPreviewDocument;

	protected final SourceViewer fSourceViewer;

	protected final IPreferenceStore fPreferenceStore;

	protected final MarginPainter fMarginPainter;

	protected Map fWorkingValues;

	private int fTabSize = 0;

	/**
	 * Create a new Ruby preview
	 * 
	 * @param workingValues
	 * @param parent
	 */
	public RubyPreview(Map workingValues, Composite parent) {
		RubyTextTools tools = RubyPlugin.getDefault().getRubyTextTools();
		fPreviewDocument = new Document();
		fWorkingValues = workingValues;
		tools.setupRubyDocumentPartitioner(fPreviewDocument,
				IRubyPartitions.RUBY_PARTITIONING);

		IPreferenceStore[] chain = { RubyPlugin.getDefault()
				.getCombinedPreferenceStore() };
		fPreferenceStore = new ChainedPreferenceStore(chain);
		fSourceViewer = new RubySourceViewer(parent, null, null, false,
				SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER,
				fPreferenceStore);
		fViewerConfiguration = new SimpleRubySourceViewerConfiguration(tools
				.getColorManager(), fPreferenceStore, null,
				IRubyPartitions.RUBY_PARTITIONING, true);
		fSourceViewer.configure(fViewerConfiguration);
		fSourceViewer.getTextWidget().setFont(
				JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT));

		fMarginPainter = new MarginPainter(fSourceViewer);
		final RGB rgb = PreferenceConverter
				.getColor(
						fPreferenceStore,
						AbstractDecoratedTextEditorPreferenceConstants.EDITOR_PRINT_MARGIN_COLOR);
		fMarginPainter.setMarginRulerColor(tools.getColorManager()
				.getColor(rgb));
		fSourceViewer.addPainter(fMarginPainter);

		new RubySourcePreviewerUpdater();
		fSourceViewer.setDocument(fPreviewDocument);
	}

	public Control getControl() {
		return fSourceViewer.getControl();
	}
	
	public StyledText getTextWidget() {
		return fSourceViewer.getTextWidget();
	}

	public void update() {
		if (fWorkingValues == null) {
			fPreviewDocument.set(""); //$NON-NLS-1$
			return;
		}

		// update the print margin
		final String value = (String) fWorkingValues
				.get(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT);
		final int lineWidth = getPositiveIntValue(value, 0);
		fMarginPainter.setMarginRulerColumn(lineWidth);

		// update the tab size
		final int tabSize = getPositiveIntValue((String) fWorkingValues
				.get(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE), 0);
		if (tabSize != fTabSize)
			fSourceViewer.getTextWidget().setTabs(tabSize);
		fTabSize = tabSize;

		final StyledText widget = (StyledText) fSourceViewer.getControl();
		final int height = widget.getClientArea().height;
		final int top0 = widget.getTopPixel();

		final int totalPixels0 = getHeightOfAllLines(widget);
		final int topPixelRange0 = totalPixels0 > height ? totalPixels0
				- height : 0;

		widget.setRedraw(false);
		doFormatPreview();
		fSourceViewer.setSelection(null);

		final int totalPixels1 = getHeightOfAllLines(widget);
		final int topPixelRange1 = totalPixels1 > height ? totalPixels1
				- height : 0;

		final int top1 = topPixelRange0 > 0 ? (int) (topPixelRange1 * top0 / (double) topPixelRange0)
				: 0;
		widget.setTopPixel(top1);
		widget.setRedraw(true);
	}

	private int getHeightOfAllLines(StyledText styledText) {
		int height = 0;
		int lineCount = styledText.getLineCount();
		for (int i = 0; i < lineCount; i++) {
			// TODO Uncomment the line below and delete on after when moving to
			// Eclipse 3.2
			// ECLIPSE 3.2
			// height= height +
			// styledText.getLineHeight(styledText.getOffsetAtLine(i));
			height = height + styledText.getLineHeight();
		}
		return height;
	}

	protected abstract void doFormatPreview();

	private static int getPositiveIntValue(String string, int defaultValue) {
		try {
			int i = Integer.parseInt(string);
			if (i >= 0) {
				return i;
			}
		} catch (NumberFormatException e) {
		}
		return defaultValue;
	}

	public final Map getWorkingValues() {
		return fWorkingValues;
	}

	public final void setWorkingValues(Map workingValues) {
		fWorkingValues = workingValues;
	}
}
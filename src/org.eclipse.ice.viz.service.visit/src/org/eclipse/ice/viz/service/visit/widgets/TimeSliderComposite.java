/*******************************************************************************
 * Copyright (c) 2015 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jordan Deyton (UT-Battelle, LLC.) - Initial API and implementation and/or
 *     initial documentation
 *   Jordan Deyton - bug 471166
 *   Jordan Deyton - bug 471248
 *******************************************************************************/
package org.eclipse.ice.viz.service.visit.widgets;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ice.client.common.ActionTree;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Text;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * This class provides a widget with three separate means of selecting times
 * from a list of allowed times:
 * <ul>
 * <li>dragging a {@link Scale} widget (coarse-grained control)</li>
 * <li>pressing arrow keys (fine-grained control)</li>
 * <li>typing a value near an existing timestep (very fine-grained control)</li>
 * </ul>
 * As input, the widget takes an ordered list of doubles. If the input list is
 * <i>not</i> in ascending order, then the widget will order them on its own and
 * provide timesteps based on the ordered list.
 * <p>
 * To receive notifications of changes to the selected timestep, a
 * {@link SelectionListener} must be registered as with a typical SWT widget.
 * Notifications will be posted on the UI thread.
 * </p>
 * 
 * @author Jordan Deyton
 *
 */
public class TimeSliderComposite extends Composite {

	/**
	 * The widget used for coarse-grained timestep control.
	 */
	private final Scale scale;
	/**
	 * The text widget used for exact timestep control.
	 */
	private final Text text;
	/**
	 * Sets the timestep to the next available timestep. This is a fine-grained
	 * control.
	 */
	private final Button nextButton;
	/**
	 * Sets the timestep to the previous available timestep. This is a
	 * fine-grained control.
	 */
	private final Button prevButton;
	/**
	 * Starts or pauses playback of the timesteps.
	 */
	private final Button playButton;
	/**
	 * Opens a context menu for changing playback settings for the widget.
	 */
	private final Button optionsButton;
	/**
	 * The Menu that appears beneath the options button.
	 */
	private final MenuManager optionsMenuManager;

	/**
	 * A minimum FPS of 1 frame per minute.
	 */
	private static final double minFPS = 0.0167;
	/**
	 * The current frames per second for playback.
	 */
	private double fps = 1.0;
	/**
	 * The current delay between playback events produced by the FPS. This is
	 * used when scheduling the next event, which requires an int to represent
	 * the delay in milliseconds.
	 */
	private int fpsDelay = 1000;
	/**
	 * Whether or not the playback operation is currently running.
	 */
	private boolean isPlaying = false;
	/**
	 * The runnable operation used for "playback". It increments the timestep
	 * and schedules itself to execute later based on the value of
	 * {@link #playbackInterval}.
	 */
	private Runnable playbackRunnable;

	/**
	 * The current timestep, or -1 if there are no times available.
	 */
	private int timestep;
	/**
	 * A tree that contains the times associated with the timesteps.
	 */
	private BinarySearchTree times;

	/**
	 * The image used for the "next" button.
	 */
	private static Image nextImage;
	/**
	 * The image used for the "options" button.
	 */
	private static Image optionsImage;
	/**
	 * The image used for the "pause" button.
	 */
	private static Image pauseImage;
	/**
	 * The image used for the "play" button.
	 */
	private static Image playImage;
	/**
	 * The image used for the "previous" button.
	 */
	private static Image prevImage;

	/**
	 * The string to use in the text box when there are no times configured.
	 */
	private static final String NO_TIMES = "N/A";

	/**
	 * A list of listeners that will be notified if the timestep changes to a
	 * new value.
	 */
	private final List<SelectionListener> listeners;

	/**
	 * The default constructor.
	 * 
	 * @param parent
	 *            a widget which will be the parent of the new instance (cannot
	 *            be null)
	 * @param style
	 *            the style of widget to construct
	 */
	public TimeSliderComposite(Composite parent, int style) {
		super(parent, style);

		// Iniitalize the list of selection listeners.
		listeners = new ArrayList<SelectionListener>();

		// Create the widgets.
		prevButton = createPrevButton(this);
		playButton = createPlayButton(this);
		nextButton = createNextButton(this);
		optionsButton = createOptionsButton(this);
		text = createText(this);
		scale = createScale(this);

		// Create the Menu for the options button.
		optionsMenuManager = createOptionsMenuManager(this);

		// Layout the widgets. The scale should take up all horizontal space on
		// the right. The text widget should grab whatever space remains, while
		// the normal buttons take up only the space they require.
		setLayout(new GridLayout(6, false));
		GridData gridData = new GridData(SWT.CENTER, SWT.CENTER, false, true);
		prevButton.setLayoutData(gridData);
		playButton.setLayoutData(GridDataFactory.copyData(gridData));
		nextButton.setLayoutData(GridDataFactory.copyData(gridData));
		optionsButton.setLayoutData(GridDataFactory.copyData(gridData));
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, true));
		scale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

		// The default focus should be on the play button.
		playButton.setFocus();

		// Refresh the widgets. This will enable/disable them as necessary.
		setTimes(new ArrayList<Double>());

		return;
	}

	/**
	 * Creates the "next" button that increments the timestep.
	 * 
	 * @param parent
	 *            The parent Composite for this widget. Assumed not to be
	 *            {@code null}.
	 * @return The new widget.
	 */
	private Button createNextButton(Composite parent) {
		Button nextButton = new Button(parent, SWT.PUSH);

		// When the button is clicked, playback should be stopped and the
		// timestep should be incremented.
		nextButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// Disable playback.
				setPlayback(false, e);

				// Increment the timestep.
				if (incrementTimestep()) {
					notifyListeners(e);
				}
			}
		});

		// Load the button image as necessary.
		if (nextImage == null) {
			nextImage = loadImage("nav_forward.gif");
		}

		// Set the initial tool tip and image.
		nextButton.setToolTipText("Next");
		nextButton.setImage(nextImage);

		return nextButton;
	}

	/**
	 * Creates the "options" button that can be used to configure playback
	 * behavior.
	 * 
	 * @param parent
	 *            The parent Composite for this widget. Assumed not to be
	 *            {@code null}.
	 * @return The new widget.
	 */
	private Button createOptionsButton(Composite parent) {
		final Button optionsButton = new Button(parent, SWT.PUSH);

		// When the button is clicked, playback should be stopped and the
		// timestep should be incremented.
		optionsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// Open up the menu below this button.
				Rectangle r = optionsButton.getBounds();
				Point p = new Point(r.x, r.y + r.height);
				p = optionsButton.getParent().toDisplay(p.x, p.y);
				Menu menu = optionsMenuManager.getMenu();
				menu.setLocation(p.x, p.y);
				menu.setVisible(true);
			}
		});

		// Load the button image as necessary.
		if (optionsImage == null) {
			optionsImage = loadImage("thread_obj.gif");
		}

		// Set the initial tool tip and image.
		optionsButton.setToolTipText("Playback settings");
		optionsButton.setImage(optionsImage);

		return optionsButton;
	}

	/**
	 * Creates the context menu that appears when the "options" button is
	 * clicked.
	 * 
	 * @param parent
	 *            The parent Composite for this widget (a context Menu). Assumed
	 *            not to be {@code null}.
	 * @return The new MenuManager for the "options" context Menu.
	 */
	private MenuManager createOptionsMenuManager(Composite parent) {
		MenuManager manager = new MenuManager();

		// Add a sub-menu for selecting the playback rate.
		ActionTree playbackRate = new ActionTree("Playback Rate");

		// Set up the list of previous rates. This will be used to keep track of
		// previous values when selecting a new custom framerate.
		final List<String> previousRates = new ArrayList<String>();
		previousRates.add("1");
		previousRates.add("12");
		previousRates.add("24");
		previousRates.add("30");

		// Add some default framerates for 1, 12, 24, and 30 fps.
		playbackRate.add(new ActionTree(new Action("1 fps") {
			@Override
			public void run() {
				setFPS(1.0);
			}
		}));
		playbackRate.add(new ActionTree(new Action("12 fps") {
			@Override
			public void run() {
				setFPS(12.0);
			}
		}));
		playbackRate.add(new ActionTree(new Action("24 fps") {
			@Override
			public void run() {
				setFPS(24.0);
			}
		}));
		playbackRate.add(new ActionTree(new Action("30 fps") {
			@Override
			public void run() {
				setFPS(30.0);
			}
		}));
		// Add an action for selecting a custom framerate.
		playbackRate.add(new ActionTree(new Action("Custom...") {
			@Override
			public void run() {
				// Set up a dialog based on the previous and current framerate
				// values. It should use a Combo for selection, and should allow
				// the user to enter a new double framerate.
				ComboDialog dialog = new ComboDialog(getShell(), false) {
					@Override
					protected String validateSelection(String text) {
						String validatedText = super.validateSelection(text);
						// Accept double values that are greater than the min.
						if (validatedText == null) {
							try {
								double newValue = Double.parseDouble(text);
								if (Double.compare(newValue, minFPS) >= 0) {
									validatedText = text;
								}
							} catch (NullPointerException | NumberFormatException exception) {
								// Nothing to do.
							}
						}
						return validatedText;
					}
				};
				// Customize the dialog's appearance.
				dialog.setInfoText("Enter a new frame rate or\n" + "select a previous rate.\n"
						+ "Values must be greater than 0.0");
				dialog.setErrorText("Please enter a number greater than 60\n" + "seconds per frame (0.0167 FPS).");
				// Set the dialog Combo's allowed values and initial value.
				dialog.setAllowedValues(previousRates);
				dialog.setInitialValue(Double.toString(fps));

				// Open the dialog and get the results. The selection has
				// already been validated if OK is clicked.
				if (dialog.open() == Window.OK) {
					// Get the resulting value as a string.
					String value = dialog.getValue();
					previousRates.add(value);
					// Convert it to a double and set it as the new rate.
					double newRate = Double.parseDouble(value);
					setFPS(newRate);
				}

				return;
			}
		}));
		manager.add(playbackRate.getContributionItem());

		// Create the manager's context Menu.
		manager.createContextMenu(parent);

		return manager;
	}

	/**
	 * Creates the "play" button that toggles the playback operation.
	 * 
	 * @param parent
	 *            The parent Composite for this widget. Assumed not to be
	 *            {@code null}.
	 * @return The new widget.
	 */
	private Button createPlayButton(Composite parent) {
		Button playButton = new Button(parent, SWT.PUSH);

		// When the button is clicked, playback should be toggled.
		playButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// Toggle playback.
				setPlayback(!isPlaying, e);
			}
		});

		// Load the play and pause button images as necessary.
		if (playImage == null) {
			playImage = loadImage("nav_go.gif");
		}
		if (pauseImage == null) {
			pauseImage = loadImage("suspend_co.gif");
		}

		// Set the initial tool tip and image.
		playButton.setToolTipText("Play");
		playButton.setImage(playImage);

		return playButton;
	}

	/**
	 * Creates the "previous" button that deccrements the timestep.
	 * 
	 * @param parent
	 *            The parent Composite. Assumed not to be {@code null}.
	 * @return The new button.
	 */
	private Button createPrevButton(Composite parent) {
		Button prevButton = new Button(parent, SWT.PUSH);

		// When the button is clicked, playback should be stopped and the
		// timestep should be decremented.
		prevButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// Disable playback.
				setPlayback(false, e);

				// Decrement the timestep.
				if (decrementTimestep()) {
					notifyListeners(e);
				}
			}
		});

		// Load the button image as necessary.
		if (prevImage == null) {
			prevImage = loadImage("nav_backward.gif");
		}

		// Set the initial tool tip and image.
		prevButton.setToolTipText("Previous");
		prevButton.setImage(prevImage);

		return prevButton;
	}

	/**
	 * Creates the scale or slider widget that can be used to quickly traverse
	 * the timesteps.
	 * 
	 * @param parent
	 *            The parent Composite for this widget. Assumed not to be
	 *            {@code null}.
	 * @return The new widget.
	 */
	private Scale createScale(Composite parent) {

		final Scale scale = new Scale(this, SWT.HORIZONTAL);
		scale.setMinimum(0);
		scale.setIncrement(1);
		scale.setMaximum(0);
		scale.setToolTipText("Traverses the timesteps");

		scale.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// Disable playback.
				setPlayback(false, e);

				// Get the timestep from the scale widget.
				if (setValidTimestep(scale.getSelection())) {
					notifyListeners(e);
				}
			}
		});

		return scale;
	}

	/**
	 * Creates the text widget for setting this time widget to a specific time.
	 * 
	 * @param parent
	 *            The parent Composite for this widget. Assumed not to be
	 *            {@code null}.
	 * @return The new widget.
	 */
	private Text createText(Composite parent) {

		final Text text = new Text(this, SWT.SINGLE | SWT.BORDER);

		text.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}

			@Override
			public void widgetSelected(SelectionEvent e) {
				// Disable playback.
				setPlayback(false, e);

				// Try to find the value from the text widget's new text.
				try {
					double value = Double.parseDouble(text.getText());
					// Set the value to the nearest allowed time.
					if (setValidTimestep(times.findNearestIndex(value))) {
						notifyListeners(e);
					} else {
						// Update the text to the set time regardless of whether
						// it changed. This lets the user know their input was
						// accepted.
						text.setText(Double.toString(getTime()));
					}
				} catch (NumberFormatException exception) {
					// If the number was invalid, revert to the previous text.
					text.setText(Double.toString(getTime()));
				}

				return;
			}
		});

		// Tweak the default appearance.
		text.setFont(parent.getFont());

		// Set the initial tool tip.
		text.setToolTipText("The current time");

		return text;
	}

	/**
	 * A convenient operation for loading a custom image resource from this
	 * widget's path.
	 * 
	 * @param name
	 *            The name of (path to) the image to load, like "/image1.gif".
	 * @return The loaded image, or {@code null} if the image could not be
	 *         loaded.
	 */
	private Image loadImage(String name) {
		Bundle bundle = FrameworkUtil.getBundle(TimeSliderComposite.class);
		URL url = bundle.getEntry("icons/" + name);
		return ImageDescriptor.createFromURL(url).createImage();
	}

	// ---- Public Setters and Getters ---- //

	/**
	 * Gets the current playback rate in frames per second.
	 * 
	 * @return The current frames per second for playback.
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public double getFPS() {
		// Check that this widget can be accessed.
		checkWidget();
		return fps;
	}

	/**
	 * Gets the currently selected time.
	 * 
	 * @return The selected time.
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public double getTime() {
		// Check that this widget can be accessed.
		checkWidget();
		return timestep >= 0 ? times.get(timestep) : 0.0;
	}

	/**
	 * Gets the currently selected timestep (the index of the selected time).
	 * 
	 * @return The selected timestep.
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public int getTimestep() {
		// Check that this widget can be accessed.
		checkWidget();
		return timestep;
	}

	/**
	 * Starts playback on the widget. Calling this method <i>will not itself
	 * notify SelectionListeners</i>, but the ensuing playback <i>will</i>.
	 * 
	 * @return True if the widget was paused and is now playing, false
	 *         otherwise.
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public boolean play() {
		// Check that this widget can be accessed.
		checkWidget();

		boolean changed = false;

		if (!isPlaying) {
			setPlayback(true, createBlankSelectionEvent());
			changed = true;
		}

		return changed;
	}

	/**
	 * Stops playback on the widget at its current timestep.
	 * 
	 * @return True if the widget was playing and is now paused, false
	 *         otherwise.
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public boolean pause() {
		// Check that this widget can be accessed.
		checkWidget();

		boolean changed = false;

		if (isPlaying) {
			setPlayback(false, createBlankSelectionEvent());
			changed = true;
		}

		return changed;
	}

	/**
	 * Gets whether the widget is currently playing.
	 * 
	 * @return True if the widget is playing, false if it is paused.
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public boolean isPlaying() {
		// Check that this widget can be accessed.
		checkWidget();
		return isPlaying;
	}

	/*
	 * Overrides a method from Control.
	 */
	@Override
	public void setBackground(Color color) {
		super.setBackground(color);

		// Update the background colors for all child widgets.
		scale.setBackground(color);
		playButton.setBackground(color);
		nextButton.setBackground(color);
		prevButton.setBackground(color);

		return;
	}

	/**
	 * Sets the current playback rate in frames per second. The slowest allowed
	 * framerate is defined by {@link #minFPS}.
	 * 
	 * @param fps
	 *            The new framerate. Must be greater than the minimum allowed
	 *            framerate.
	 * @return True if the FPS was changed, false otherwise.
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public boolean setFPS(double fps) {
		// Check that this widget can be accessed.
		checkWidget();

		boolean changed = false;
		if (Double.compare(fps, minFPS) >= 0 && Math.abs(fps - this.fps) > 1e-7) {
			this.fps = fps;
			// Convert the FPS into a millisecond delay.
			fpsDelay = (int) (Math.round(1000.0 / this.fps));
			changed = true;
		}
		return changed;
	}

	/**
	 * Updates the timestep and all embedded widgets based on the nearest known
	 * time to the specified time. Calling this method <i>will not notify
	 * SelectionListeners!</i>
	 * <p>
	 * <b>Note:</b> Calling this method from the UI thread with valid arguments
	 * will pause the widget if it is currently playing.
	 * </p>
	 * 
	 * @param time
	 *            The new time.
	 * @return True if the timestep changed, false otherwise.
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public boolean setTime(double time) {
		// Check that this widget can be accessed.
		checkWidget();

		// Halt playback.
		SelectionEvent event = createBlankSelectionEvent();
		setPlayback(false, event);

		// Set the timestep.
		return setValidTimestep(times.findNearestIndex(time));
	}

	/**
	 * Sets the list of timesteps used by this widget.
	 * <p>
	 * <b>Note:</b> Calling this method from the UI thread with valid arguments
	 * will pause the widget if it is currently playing.
	 * </p>
	 * 
	 * @param times
	 *            The list of timesteps. Null values and duplicates will be
	 *            ignored. The list will be ordered.
	 * @exception IllegalArgumentException
	 *                <ul>
	 *                <li>ERROR_NULL_ARGUMENT - if the list of times is null
	 *                </li>
	 *                </ul>
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public void setTimes(List<Double> times) {
		// Check that this widget can be accessed. Also check the list is not
		// null.
		checkWidget();
		if (times == null) {
			throw new SWTException(SWT.ERROR_NULL_ARGUMENT);
		}

		// Halt playback.
		setPlayback(false, createBlankSelectionEvent());

		// Try to recreate the tree of times based on the new list of times.
		try {
			this.times = new BinarySearchTree(times);
			timestep = 0;
		}
		// If the list has no non-null values, set the timestep to -1.
		catch (IllegalArgumentException e) {
			this.times = null;
			timestep = -1;
		}

		// Refresh the widgets.
		final int size = this.times != null ? this.times.size() : 0;
		boolean widgetsEnabled = size > 1;

		// Enable/disable the widgets as necessary.
		scale.setEnabled(widgetsEnabled);
		text.setEnabled(widgetsEnabled);
		nextButton.setEnabled(widgetsEnabled);
		prevButton.setEnabled(widgetsEnabled);
		playButton.setEnabled(widgetsEnabled);

		// Refresh the scale widget's max value.
		scale.setMaximum(widgetsEnabled ? size - 1 : 0);

		// Reset the selection of the widgets.
		scale.setSelection(0);
		text.setText(size > 0 ? Double.toString(getTime()) : NO_TIMES);

		return;
	}

	/**
	 * Updates the timestep and all embedded widgets based on the new timestep
	 * value. Calling this method <i>will not notify SelectionListeners!</i>
	 * <p>
	 * <b>Note:</b> Calling this method from the UI thread with valid arguments
	 * will pause the widget if it is currently playing.
	 * </p>
	 * 
	 * @param index
	 *            The new index of the timestep.
	 * @return True if the timestep changed, false otherwise.
	 * @throws IndexOutOfBoundsException
	 *             If the specified index is invalid.
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public boolean setTimestep(int index) throws IndexOutOfBoundsException {
		// Check that this widget can be accessed.
		checkWidget();

		// Check that the index is valid.
		if (times == null || index < 0 || index >= times.size()) {
			throw new IndexOutOfBoundsException();
		}

		// Halt playback.
		SelectionEvent event = createBlankSelectionEvent();
		setPlayback(false, event);

		// Set the timestep.
		return setValidTimestep(index);
	}

	// ------------------------------------ //

	/**
	 * Creates a blank SelectionEvent that can be used when pausing the
	 * playback.
	 * 
	 * @return A new, empty SelectionEvent associated with this widget.
	 */
	private SelectionEvent createBlankSelectionEvent() {
		Event event = new Event();
		event.widget = this;
		return new SelectionEvent(event);
	}

	/**
	 * Decrements the timestep. Updates all widgets as necessary. This will loop
	 * back around to the last timestep if necessary.
	 * 
	 * @return True if the timestep changed, false otherwise.
	 */
	private boolean decrementTimestep() {
		int size = times.size();
		return setValidTimestep((timestep - 1 + size) % size);
	}

	/**
	 * Increments the timestep. Updates all widgets as necessary. This will loop
	 * back around to the first timestep if necessary.
	 * 
	 * @return True if the timestep changed, false otherwise.
	 */
	private boolean incrementTimestep() {
		return setValidTimestep((timestep + 1) % times.size());
	}

	/**
	 * Starts or stops the playback operation.
	 * 
	 * @param play
	 *            If true, playback will be started. If false, playback will be
	 *            stopped.
	 * @param e
	 *            The selection event that triggered the playback operation.
	 *            This is only used if play is true. Otherwise, you may use
	 *            {@code null}.
	 */
	private void setPlayback(boolean play, final SelectionEvent e) {
		if (play != this.isPlaying) {
			this.isPlaying = play;

			// Determine the text and image for the play/pause button as well as
			// whether the playback event should be scheduled or cancelled.
			final String text;
			final Image image;
			final int time;
			if (play) {
				// Set the text for the pause button.
				text = "Pause";
				image = pauseImage;

				// The playback runnable should be created.
				time = fpsDelay;
				playbackRunnable = new Runnable() {
					@Override
					public void run() {
						getDisplay().timerExec(fpsDelay, this);
						if (incrementTimestep()) {
							notifyListeners(e);
						}
					}
				};
			} else {
				// Set the text for the play button.
				text = "Play";
				image = playImage;

				// The playback runnable should be cancelled.
				time = -1;
			}

			// Update the tool tip and image for the play button.
			playButton.setToolTipText(text);
			playButton.setImage(image);

			// Schedule or cancel the playback task.
			getDisplay().timerExec(time, playbackRunnable);
		}
		return;
	}

	/**
	 * Updates the timestep and all embedded widgets based on the new timestep
	 * value.
	 * <p>
	 * <b>Note:</b> This method should be used when on the UI thread and the
	 * timestep is known to be valid (e.g., from a widget's selection).
	 * </p>
	 * 
	 * @param index
	 *            The new timestep. <i>Assumed to be valid.</i>
	 * @return True if the timestep changed to a new value, false otherwise.
	 */
	private boolean setValidTimestep(int index) {
		boolean changed = false;
		if (index != timestep) {

			changed = true;

			// Update the timestep.
			timestep = index;

			// Update the selections on the widgets.
			scale.setSelection(timestep);
			text.setText(Double.toString(times.get(timestep)));
		}

		return changed;
	}

	// ---- SelectionListeners ---- //
	/**
	 * Adds a new listener to be notified when the selected time changes.
	 * 
	 * @param listener
	 *            The new listener to add.
	 * 
	 * @exception IllegalArgumentException
	 *                <ul>
	 *                <li>ERROR_NULL_ARGUMENT - if the listener is null</li>
	 *                </ul>
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public void addSelectionListener(SelectionListener listener) {
		// Check that this widget can be accessed. Also check the listener is
		// not null.
		checkWidget();
		if (listener == null) {
			throw new SWTException(SWT.ERROR_NULL_ARGUMENT);
		}

		listeners.add(listener);
	}

	/**
	 * Removes the specified listener from this widget. It will no longer be
	 * notified of time changes from this widget unless it has been registered
	 * multiple times.
	 * 
	 * @param listener
	 *            The listener to remove.
	 * 
	 * @exception IllegalArgumentException
	 *                <ul>
	 *                <li>ERROR_NULL_ARGUMENT - if the listener is null</li>
	 *                </ul>
	 * @exception SWTException
	 *                <ul>
	 *                <li>ERROR_WIDGET_DISPOSED - if the receiver has been
	 *                disposed</li>
	 *                <li>ERROR_THREAD_INVALID_ACCESS - if not called from the
	 *                thread that created the receiver</li>
	 *                </ul>
	 */
	public void removeSelectionListener(SelectionListener listener) {
		// Check that this widget can be accessed. Also check the listener is
		// not null.
		checkWidget();
		if (listener == null) {
			throw new SWTException(SWT.ERROR_NULL_ARGUMENT);
		}
		listeners.remove(listener);
	}

	/**
	 * Notifies listeners that the selected time has changed. This is performed
	 * on the UI thread as with all SWT selection events.
	 * 
	 * @param e
	 *            The event triggering the updated time.
	 */
	private void notifyListeners(SelectionEvent e) {
		e.data = getTime();
		for (SelectionListener listener : listeners) {
			listener.widgetSelected(e);
		}
	}
	// ---------------------------- //
}

/*************************************************************************************
 * Copyright (c) 2010-2013 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     JBoss by Red Hat - Initial implementation.
 ************************************************************************************/
package org.jboss.tools.runtime.ui.internal.wizard;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.progress.IProgressService;
import org.jboss.tools.foundation.core.ecf.URLTransportUtility;
import org.jboss.tools.runtime.core.model.DownloadRuntime;
import org.jboss.tools.runtime.core.model.RuntimeDefinition;
import org.jboss.tools.runtime.core.model.RuntimePath;
import org.jboss.tools.runtime.core.util.RuntimeInitializerUtil;
import org.jboss.tools.runtime.ui.Messages;
import org.jboss.tools.runtime.ui.RuntimeUIActivator;

/**
 * 
 * @author snjeza
 *
 */
public class DownloadRuntimesSecondPage extends WizardPage {

	private static final String URL_IS_NOT_VALID = Messages.DownloadRuntimesSecondPage_URL_is_not_valid;
	//private static final String SELECTED_RUNTIME_REQUIRED = "A runtime must be selected";//$NON-NLS-1$
	private static final String SEPARATOR = "/"; //$NON-NLS-1$
	private static final String FOLDER_IS_REQUIRED = Messages.DownloadRuntimesSecondPage_This_folder_is_required;
	private static final String FOLDER_IS_NOT_WRITABLE = Messages.DownloadRuntimesSecondPage_This_folder_does_not_exist;
	private static final String DELETE_ON_EXIT = "deleteOnExit"; //$NON-NLS-1$
	private static final String JAVA_IO_TMPDIR = "java.io.tmpdir"; //$NON-NLS-1$
	private static final String USER_HOME = "user.home"; //$NON-NLS-1$
	private static final String DEFAULT_DIALOG_PATH = "defaultDialogPath"; //$NON-NLS-1$
		
	private static final String DEFAULT_DESTINATION_PATH = "defaultDestinationPath"; //$NON-NLS-1$

	private IDialogSettings dialogSettings;
	private Button deleteOnExit;
	private Text destinationPathText;
	private Text pathText;
	private DownloadRuntime downloadRuntime; 
	//private List<DownloadRuntime> downloadRuntimes; 
	private String delete;
	private ControlDecoration decPathError;
	private ControlDecoration decPathReq;
	private ControlDecoration destinationPathError;
	private ControlDecoration destinationPathReq;
	//private ControlDecoration selectRuntimeError;
	//private Combo runtimesCombo;
	private Link urlText;
	private Group warningComposite;
	private Label warningLabel;
	private Link warningLink;
	private Composite contents;
	private Shell shell;
	private Composite pathComposite;
	
	IOverwrite overwriteQuery = new IOverwrite() {
		public int overwrite(File file) {
			final String msg = NLS.bind(Messages.DownloadRuntimesSecondPage_The_file_already_exists, file.getAbsolutePath()); 
			final String[] options = { IDialogConstants.YES_LABEL,
					IDialogConstants.YES_TO_ALL_LABEL,
					IDialogConstants.NO_LABEL,
					IDialogConstants.NO_TO_ALL_LABEL,
					IDialogConstants.CANCEL_LABEL };
			final int[] retVal = new int[1];
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					Shell shell = PlatformUI.getWorkbench().getModalDialogShellProvider().getShell();
					MessageDialog dialog = new MessageDialog(shell, Messages.DownloadRuntimesSecondPage_Question,
							null, msg, MessageDialog.QUESTION, options, 0) {
						protected int getShellStyle() {
							return super.getShellStyle() | SWT.SHEET;
						}
					};
					dialog.open();
					retVal[0] = dialog.getReturnCode();
				}
			});
			return retVal[0];
		}
	};

	
	public DownloadRuntimesSecondPage(DownloadRuntime downloadRuntime, Shell shell) {
		super("downloadRuntimesSecondPage"); //$NON-NLS-1$
		this.downloadRuntime = downloadRuntime;
		this.shell = shell;
		setTitle(Messages.DownloadRuntimesSecondPage_Download_Runtime);
		setDescription();
		dialogSettings = RuntimeUIActivator.getDefault().getDialogSettings();
	}

	private void setDescription() {
		if (downloadRuntime != null) {
			setDescription("Download Runtime '" + downloadRuntime.getName() + "'");//$NON-NLS-1$ //$NON-NLS-2$
		} else {
			setDescription("Download Runtime");//$NON-NLS-1$
		}
	}

	@Override
	public void createControl(Composite parent) {
		contents = new Composite(parent, SWT.NONE);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		
		contents.setLayoutData(gd);
		contents.setLayout(new GridLayout(1, false));
				
		pathComposite = createAndFillPathComposite(contents);

		//validationGroup = createEAPValidation(contents);
		
		//warningComposite = createWarningComposite(contents);

		setDownloadRuntime(downloadRuntime);
		
		setControl(contents);
		refresh();
	}

	private Composite createAndFillPathComposite(Composite parent) {
		Composite pathComposite = new Composite(parent, SWT.NONE);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		pathComposite.setLayoutData(gd);
		pathComposite.setLayout(new GridLayout(3, false));
		
		Label urlLabel = new Label(pathComposite, SWT.NONE);
		urlLabel.setText(Messages.DownloadRuntimesSecondPage_URL);
		urlText = new Link(pathComposite, SWT.NONE);
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.horizontalSpan=2;
		urlText.setLayoutData(gd);
		urlText.addSelectionListener( new SelectionAdapter( ) {

			public void widgetSelected( SelectionEvent e )
			{
				String t = e.text;
				String humanUrl = downloadRuntime.getHumanUrl();
				if (humanUrl != null && t.contains(humanUrl)) {
					IWorkbenchBrowserSupport support = PlatformUI.getWorkbench()
							.getBrowserSupport();
					try {
						URL url = new URL(humanUrl);
						support.getExternalBrowser().openURL(url);
					} catch (Exception e1) {
						RuntimeUIActivator.log(e1);
						final String message = e1.getMessage();
						Display.getDefault().syncExec(new Runnable() {

							@Override
							public void run() {
								MessageDialog.openError(getActiveShell(), "Error", message);//$NON-NLS-1$
							}
							
						});
					}
				}
				
			}
		} );
		
		if( requiresManualDownload()) {
			// Do not make the remainder of the widgets
			return pathComposite;
		}

		Label pathLabel = new Label(pathComposite, SWT.NONE);
		pathLabel.setText(Messages.DownloadRuntimesSecondPage_Install_folder);
		
		pathText = new Text(pathComposite, SWT.BORDER);
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		pathText.setLayoutData(gd);
		final String defaultPath = getDefaultPath();
		pathText.setText(defaultPath);
		decPathError = addDecoration(pathText, FieldDecorationRegistry.DEC_ERROR, FOLDER_IS_NOT_WRITABLE);
		decPathReq = addDecoration(pathText, FieldDecorationRegistry.DEC_REQUIRED, FOLDER_IS_REQUIRED);
		pathText.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				validate();
			}
		});
		
		Button browseButton = new Button(pathComposite, SWT.NONE);
		browseButton.setText(Messages.DownloadRuntimesSecondPage_Browse);
		browseButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(getShell());
				dialog.setMessage(Messages.DownloadRuntimesSecondPage_Select_install_folder);
				dialog.setFilterPath(pathText.getText());
				final String path = dialog.open();
				if (path == null) {
					return;
				}
				pathText.setText(path);
			}
		
		});
		
		Label destinationLabel = new Label(pathComposite, SWT.NONE);
		destinationLabel.setText(Messages.DownloadRuntimesSecondPage_Download_folder);
		
		destinationPathText = new Text(pathComposite, SWT.BORDER);
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		destinationPathText.setLayoutData(gd);
		String destinationPath = dialogSettings.get(DEFAULT_DESTINATION_PATH);
		destinationPathError = addDecoration(destinationPathText, FieldDecorationRegistry.DEC_ERROR, FOLDER_IS_NOT_WRITABLE);
		destinationPathReq = addDecoration(destinationPathText, FieldDecorationRegistry.DEC_REQUIRED, FOLDER_IS_REQUIRED);
		
		if (destinationPath == null || destinationPath.isEmpty()) {
			destinationPath=System.getProperty(JAVA_IO_TMPDIR);
		}
		destinationPathText.setText(destinationPath);
		Button browseDestinationButton = new Button(pathComposite, SWT.NONE);
		browseDestinationButton.setText(Messages.DownloadRuntimesSecondPage_Browse);
		browseDestinationButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(getShell());
				dialog.setMessage(Messages.DownloadRuntimesSecondPage_Select_ownload_folder);
				dialog.setFilterPath(destinationPathText.getText());
				final String path = dialog.open();
				if (path == null) {
					return;
				}
				destinationPathText.setText(path);
			}
		
		});
		
		destinationPathText.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				validate();
			}
		});
		
		deleteOnExit = new Button(pathComposite, SWT.CHECK);
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		gd.horizontalSpan=3;
		deleteOnExit.setLayoutData(gd);
		deleteOnExit.setText(Messages.DownloadRuntimesSecondPage_Delete_archive_after_installing);
		
		delete = dialogSettings.get(DELETE_ON_EXIT);
		if (delete == null) {
			delete = Boolean.TRUE.toString();
		}
		deleteOnExit.setSelection(new Boolean(delete));
		deleteOnExit.addSelectionListener(new SelectionAdapter() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				delete = new Boolean(deleteOnExit.getSelection()).toString();
			}
		});
		
		return pathComposite;
	}
	
	private Group createWarningComposite(Composite parent) {
		warningComposite = new Group(parent, SWT.NONE);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		//gd.horizontalSpan = 3;
		warningComposite.setLayoutData(gd);
		warningComposite.setLayout(new GridLayout(1, false));
		warningComposite.setText(Messages.DownloadRuntimesSecondPage_Warning);
		
		warningLabel = new Label(warningComposite, SWT.NONE);
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		warningLabel.setLayoutData(gd);
		warningLink = new Link(warningComposite, SWT.NONE);
		gd = new GridData(SWT.FILL, SWT.FILL, true, false);
		warningLink.setLayoutData(gd);
		
		warningLink.addSelectionListener( new SelectionAdapter( ) {

			public void widgetSelected( SelectionEvent e )
			{
				String text = e.text;
				String humanUrl = downloadRuntime == null ? null : downloadRuntime.getHumanUrl();
				String linkUrl = null; 
				if (humanUrl != null && "link".equals(text)) {//$NON-NLS-1$
					linkUrl = humanUrl;
				} else if ("Show Details".equals(text)) {//$NON-NLS-1$
					linkUrl = "http://www.redhat.com/jboss/";//$NON-NLS-1$
				}
				
				if (linkUrl != null) {
					IWorkbenchBrowserSupport support = PlatformUI.getWorkbench()
							.getBrowserSupport();
					try {
						URL url = new URL(linkUrl); 
						support.getExternalBrowser().openURL(url);
					} catch (Exception e1) {
						RuntimeUIActivator.log(e1);
						final String message = e1.getMessage();
						Display.getDefault().syncExec(new Runnable() {

							@Override
							public void run() {
								MessageDialog.openError(getActiveShell(), "Error", message);//$NON-NLS-1$
							}
							
						});
					}
				}
				
			}
		} );
		return warningComposite;
	}

	private String getDefaultPath() {
		String defaultPath = dialogSettings.get(DEFAULT_DIALOG_PATH);
		if (defaultPath == null || defaultPath.isEmpty()) {
			defaultPath=System.getProperty(USER_HOME);
		}
		return defaultPath;
	}

	private void showDecorations() {
		if( requiresManualDownload() )
			return;
		
		String path = pathText.getText();
		String destination = destinationPathText.getText();
		decPathError.hide();
		decPathReq.hide();
		destinationPathError.hide();
		destinationPathReq.hide();
		
		if (path.isEmpty()) {
			decPathReq.show();
		}
		if (destination.isEmpty()) {
			destinationPathReq.show();
		}
		boolean pathExists = checkPath(path, decPathError);
		boolean destExists = checkPath(destination, destinationPathError);
		if (!pathExists) {
			setErrorMessage(Messages.DownloadRuntimesSecondPage_Install_folder_does_not_exist);
		} else if (path.isEmpty()) {
			setErrorMessage(Messages.DownloadRuntimesSecondPage_Install_folder_is_required);
		} else if (!destExists) {
			setErrorMessage(Messages.DownloadRuntimesSecondPage_19);
		} else if (destination.isEmpty()) {
			setErrorMessage(Messages.DownloadRuntimesSecondPage_Download_folder_is_required);
		}
		decPathError.setShowHover(true);
		setPageComplete(getErrorMessage() == null);
	}

	private boolean checkPath(String path, ControlDecoration dec) {
		if (path.isEmpty()) {
			return true;
		}
		try {
			File file = File.createTempFile("temp", "txt", new File(path));//$NON-NLS-1$ //$NON-NLS-2$
			file.deleteOnExit();
			file.delete();
		} catch (IOException e) {
			dec.show();
			return false;
		}
		return true;
	}

	protected ControlDecoration addDecoration(Control control, String id, String description) {
		final ControlDecoration decPath = new ControlDecoration(control, SWT.TOP
				| SWT.LEFT);
		FieldDecorationRegistry registry = FieldDecorationRegistry.getDefault();
		FieldDecoration fd = registry.getFieldDecoration(id);
		decPath.setImage(fd.getImage());
		fd.setDescription(description);
	
		decPath.setImage(FieldDecorationRegistry.getDefault().getFieldDecoration(
				id).getImage());

		decPath.setShowOnlyOnFocus(false);
		decPath.setShowHover(true);
		decPath.setDescriptionText(description);
		return decPath;
	}

	protected void validate() {
		setErrorMessage(null);
		showDecorations();
	}

	private void refresh() {
		// Completely remove and then re-create the warning composite (??)
		if (contents != null && !contents.isDisposed()) {
			if( pathComposite != null ) {
				pathComposite.dispose();
				pathComposite = null;
			}
			if (warningComposite != null) {
				warningComposite.dispose();
				warningComposite = null;
			}
			contents.layout(true, true);
			contents.pack();
		}
		
		if (downloadRuntime != null) {
			boolean requireManualDownload = requiresManualDownload();
			pathComposite = createAndFillPathComposite(contents);
			if (downloadRuntime.getUrl() != null) {
				urlText.setText(downloadRuntime.getUrl());
			} else if (downloadRuntime.getHumanUrl() != null){
				urlText.setText("<a>"+downloadRuntime.getHumanUrl().trim()+"</a>");//$NON-NLS-1$ //$NON-NLS-2$
			} else {
				urlText.setText(""); //$NON-NLS-1$
			}
			boolean isDisclaimer = downloadRuntime.isDisclaimer();
			boolean showWarning = isDisclaimer || requireManualDownload;
			
			if (showWarning) {
				warningComposite = createWarningComposite(contents);
				if (isDisclaimer) {
					warningLabel.setText("This is a community project and, as such is not supported with an SLA.");//$NON-NLS-1$
					warningLink.setText("If you're looking for fully supported, certified, enterprise middleware try JBoss Enterprise Middleware products. <a>Show Details</a>");//$NON-NLS-1$
				} else if (requireManualDownload) {
					warningLabel.setText("This runtime is only available as manual download.");//$NON-NLS-1$
					warningLink.setText("Please click on the download <a>link</a>.");//$NON-NLS-1$
				}
			}
		} 
		
		contents.getParent().layout(true, true);
		validate();
		updateWidgetEnablementForDownloadRuntime();
	}

	@Override
	public boolean canFlipToNextPage() {
		return super.canFlipToNextPage() && 
				downloadRuntime != null && (downloadRuntime.getLicenceURL() != null);
	}

	@Override
	public IWizardPage getPreviousPage() {
		if (downloadRuntime != null && downloadRuntime.getLicenceURL() == null) {
			int count = getWizard().getPageCount();
			if (count > 0) {
				return getWizard().getPages()[0];
			}
		}
		return super.getPreviousPage();
	}

	public void setDownloadRuntime(DownloadRuntime selectedRuntime) {
		downloadRuntime = selectedRuntime;
		setDescription();
		if (contents != null && !contents.isDisposed()) {
			refresh();
			if (downloadRuntime != null) {
				setPageComplete(true);
			} else {
				setPageComplete(true);
			}
		} else {
			setPageComplete(false);
		}
	}
	
	private void updateWidgetEnablementForDownloadRuntime() {
		if( downloadRuntime == null )
			return;
		if( !requiresManualDownload()) {
			boolean enabled = (downloadRuntime.getUrl() != null);
			deleteOnExit.setEnabled(enabled);
			destinationPathText.setEnabled(enabled);
			pathText.setEnabled(enabled);
		}
	}

	private boolean requiresManualDownload() {
		return downloadRuntime != null && downloadRuntime.getUrl() == null && downloadRuntime.getHumanUrl() != null;
	}
	
	public boolean finishPage(IProgressMonitor monitor) {
		if( requiresManualDownload() ) {
			// User is expected to have downloaded this on their own
			return true;
		}
		
		String selectedDirectory = pathText.getText();
		String destinationDirectory = destinationPathText.getText();
		boolean del = deleteOnExit.getSelection();
		return downloadRuntime(selectedDirectory, destinationDirectory, del, monitor);
	}

	private void saveDialogSettings() {
		dialogSettings.put(DEFAULT_DESTINATION_PATH,
				destinationPathText.getText());
		dialogSettings.put(DEFAULT_DIALOG_PATH, pathText.getText());
		dialogSettings.put(DELETE_ON_EXIT, delete);
	}
	
	private boolean downloadRuntime(final String selectedDirectory,
			final String destinationDirectory, final boolean deleteOnExit, IProgressMonitor monitor) {
		saveDialogSettings();
		Job downloadJob = new Job("Download '" + downloadRuntime.getName()) {//$NON-NLS-1$

			@Override
			public IStatus run(IProgressMonitor monitor) {
				monitor.beginTask("Download '" + downloadRuntime.getName() + "' ...", 100);//$NON-NLS-1$ //$NON-NLS-2$
				downloadAndInstall(selectedDirectory, destinationDirectory, 
						downloadRuntime.getUrl(), deleteOnExit, monitor);
				return Status.OK_STATUS;
			}
			
		};
		downloadJob.setUser(false);
		downloadJob.schedule();
		IProgressService progressService= PlatformUI.getWorkbench().getProgressService();
		progressService.showInDialog(getActiveShell(), downloadJob);
		return true;
	}
	
	private IStatus downloadAndInstall(String selectedDirectory, String destinationDirectory, 
			String urlString, boolean deleteOnExit, IProgressMonitor monitor) {
		FileInputStream in = null;
		OutputStream out = null;
		File file = null;
		try {
			URL url = new URL(urlString);
			String name = url.getPath();
			int slashIdx = name.lastIndexOf('/');
			if (slashIdx >= 0)
				name = name.substring(slashIdx + 1);
			
			File destination = new File(destinationDirectory);
			destination.mkdirs();
			file = new File (destination, name);
			int i = 1;
			boolean download = true;
			long urlModified = 0;
			if (deleteOnExit) {
				while (file.exists()) {
					file = new File(destination, name + "(" + i++ + ")"); //$NON-NLS-1$ //$NON-NLS-2$
				}
			} else {
				long cacheModified = file.lastModified();
				try {
					urlModified = new URLTransportUtility()
							.getLastModified(url);
					download = cacheModified <= 0 || cacheModified != urlModified;
				} catch (CoreException e) {
					// ignore
				}
			}
			if (deleteOnExit) {
				file.deleteOnExit();
			}
			IStatus result = null;
			if (download) {
				out = new BufferedOutputStream(new FileOutputStream(file));
				result = new URLTransportUtility().download(
						file.getName(), url.toExternalForm(), out, new SubProgressMonitor(monitor, 99));
				out.flush();
				out.close();
				if (urlModified > 0) {
					file.setLastModified(urlModified);
				}
			}
			if (monitor.isCanceled()) {
				file.deleteOnExit();
				file.delete();
				return Status.CANCEL_STATUS;
			}
			File directory = new File(selectedDirectory);
			directory.mkdirs();
			if (!directory.isDirectory()) {
				final String message = "The '" + directory + "' is not a directory.";//$NON-NLS-1$ //$NON-NLS-2$
				if (result != null) {
					RuntimeUIActivator.getDefault().getLog().log(result);
				} else {
					RuntimeUIActivator.log(message);
				}
				Display.getDefault().syncExec(new Runnable() {

					@Override
					public void run() {
						MessageDialog.openError(getActiveShell(), "Error", message);//$NON-NLS-1$
					}
					
				});
				file.deleteOnExit();
				file.delete();
				return Status.CANCEL_STATUS;
			}
			String root = getRoot(file, monitor);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

			final IStatus status = unzip(file, directory, monitor, overwriteQuery);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			if (!status.isOK()) {
				Display.getDefault().syncExec(new Runnable() {

					@Override
					public void run() {
						MessageDialog.openError(getActiveShell(), "Error", status.getMessage());//$NON-NLS-1$
					}
					
				});
			}
			
			if (root != null) {
				File rootFile = new File(selectedDirectory, root);
				if (rootFile != null && rootFile.exists()) {
					selectedDirectory = rootFile.getAbsolutePath();
				}
			}
			createRuntimes(selectedDirectory, monitor);
		} catch (IOException e) {
			RuntimeUIActivator.log(e);
			if (file != null && file.exists()) {
				file.deleteOnExit();
				file.delete();
			}
			final String message = e.getMessage();
			Display.getDefault().syncExec(new Runnable() {

				@Override
				public void run() {
					MessageDialog.openError(getActiveShell(), "Error", message);//$NON-NLS-1$
				}
				
			});
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// ignore
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return Status.OK_STATUS;
	}

	private String getRoot(File file, IProgressMonitor monitor) {
		ZipFile zipFile = null;
		String root = null;
		try {
			zipFile = new ZipFile(file);
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				if (monitor.isCanceled()) {
					return null;
				}
				ZipEntry entry = (ZipEntry) entries.nextElement();
				String entryName = entry.getName();
				if (entryName == null || entryName.isEmpty() 
						|| entryName.startsWith(SEPARATOR) || entryName.indexOf(SEPARATOR) == -1) {
					return null;
				}
				String directory = entryName.substring(0, entryName.indexOf(SEPARATOR));
				if (root == null) {
					root = directory;
					continue;
				}
				if (!directory.equals(root)) {
					return null;
				}
			}
		} catch (IOException e) {
			RuntimeUIActivator.log(e);
			final String message = e.getMessage();
			Display.getDefault().syncExec(new Runnable() {

				@Override
				public void run() {
					MessageDialog.openError(getActiveShell(), "Error", message);//$NON-NLS-1$
				}
				
			});
			return null;
		} finally {
			if (zipFile != null) {
				try {
					zipFile.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return root;
	}

	private Shell getActiveShell() {
		Display display = Display.getDefault();
		if (display != null) {
			if (shell == null)
				return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		}
		return shell;
	}

	private static void createRuntimes(String directory,
			IProgressMonitor monitor) {
		monitor.subTask("Creating runtime from location " + directory); //$NON-NLS-1$
		final RuntimePath runtimePath = new RuntimePath(directory);
		List<RuntimeDefinition> runtimeDefinitions = RuntimeInitializerUtil.createRuntimeDefinitions(runtimePath, monitor);
		RuntimeUIActivator.getDefault().getModel().addRuntimePath(runtimePath);
		if (runtimeDefinitions.size() == 0) {
			openRuntimeNotFoundMessage();
		} else if (runtimeDefinitions.size() > 1) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					RuntimeUIActivator.launchSearchRuntimePathDialog(
							Display.getDefault().getActiveShell(),
							RuntimeUIActivator.getDefault().getModel().getRuntimePaths(), false, 7);
				}
			});
		} else /* size == 1 */{
			RuntimeInitializerUtil.initializeRuntimes(runtimeDefinitions);
		}
		monitor.done();
	}

	private static void openRuntimeNotFoundMessage() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				MessageDialog.openError(Display.getDefault()
						.getActiveShell(), "Error", Messages.DownloadRuntimesSecondPage_No_runtime_server_found);//$NON-NLS-1$
			}
		});
	}

	private static IStatus unzip(File file, File destination,
			IProgressMonitor monitor, IOverwrite overwriteQuery) {
		ZipFile zipFile = null;
		int overwrite = IOverwrite.NO;
		destination.mkdirs();
		try {
			zipFile = new ZipFile(file);
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			monitor.done();
			monitor.beginTask(Messages.DownloadRuntimesSecondPage_Extracting, zipFile.size());
			while (entries.hasMoreElements()) {
				monitor.worked(1);
				if (monitor.isCanceled() || overwrite == IOverwrite.CANCEL) {
					return Status.CANCEL_STATUS;
				}
				ZipEntry entry = (ZipEntry) entries.nextElement();
				File entryFile = new File(destination, entry.getName());
				monitor.subTask(entry.getName());
				if (overwrite != IOverwrite.ALL && overwrite != IOverwrite.NO_ALL && entryFile.exists()) {
					overwrite = overwriteQuery.overwrite(entryFile);
					switch (overwrite) {
					case IOverwrite.CANCEL:
						return Status.CANCEL_STATUS;
					default:
						break;
					}
				}
				if (!entryFile.exists() || overwrite == IOverwrite.YES || overwrite == IOverwrite.ALL) {
					createEntry(monitor, zipFile, entry, entryFile);
				}
			}
		} catch (IOException e) {
			return new Status(IStatus.ERROR, RuntimeUIActivator.PLUGIN_ID, e.getLocalizedMessage(), e);
		} finally {
			if (zipFile != null) {
				try {
					zipFile.close();
				} catch (IOException e) {
					// ignore
				}
			}
			monitor.done();
		}
		return Status.OK_STATUS;
	}

	private static void createEntry(IProgressMonitor monitor, ZipFile zipFile,
			ZipEntry entry, File entryFile) throws IOException,
			FileNotFoundException {
		monitor.setTaskName(Messages.DownloadRuntimesSecondPage_Extracting + entry.getName());
		if (entry.isDirectory()) {
			entryFile.mkdirs();
		} else {
			entryFile.getParentFile().mkdirs();
			InputStream in = null;
			OutputStream out = null;
			try {
				in = zipFile.getInputStream(entry);
				out = new FileOutputStream(entryFile);
				copy(in, out);
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (Exception e) {
						// ignore
					}
				}
				if (out != null) {
					try {
						out.close();
					} catch (Exception e) {
						// ignore
					}
				}
			}
		}
	}
	
	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[16 * 1024];
		int len;
		while ((len = in.read(buffer)) >= 0) {
			out.write(buffer, 0, len);
		}
	}
	
}

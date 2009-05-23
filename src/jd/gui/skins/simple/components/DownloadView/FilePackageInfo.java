//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.gui.skins.simple.components.DownloadView;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import jd.config.Configuration;
import jd.controlling.DownloadController;
import jd.controlling.PasswordList;
import jd.gui.skins.simple.JTabbedPanel;
import jd.gui.skins.simple.components.ComboBrowseFile;
import jd.gui.skins.simple.components.JDFileChooser;
import jd.gui.skins.simple.components.JDTextField;
import jd.gui.skins.simple.components.multiprogressbar.MultiProgressBar;
import jd.nutils.Formatter;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.utils.JDLocale;
import jd.utils.JDTheme;
import jd.utils.JDUtilities;
import net.miginfocom.swing.MigLayout;

public class FilePackageInfo extends JTabbedPanel implements ActionListener {

    private static final long serialVersionUID = 5410296068527460629L;

    private ComboBrowseFile brwSaveTo;

    private JDTextField txtComment;

    private JDTextField txtName;

    private JDTextField txtPassword;

    private JCheckBox chbExtract;

    private JTabbedPane tabbedPane;

    private FilePackage fp = null;

    private boolean notifyUpdate = true;

    private boolean hidden = false;

    private JCheckBox chbUseSubdirectory;

    private JPanel panel;

    private MultiProgressBar progressBarFilePackage;

    private JDTextField txtpathlabel;

    private JLabel hosterlabel;

    private DownloadLink downloadLink;

    private Thread updater;

    private MultiProgressBar progressBarDownloadLink;

    private JLabel typeicon;

    private JLabel eta;

    private JLabel speed;

    private JLabel lblSize;

    private JDTextField txtURL;

    public FilePackageInfo() {
        buildGui();
        fp = null;
    }

    public void setPackage(FilePackage fp) {

        this.tabbedPane.setEnabledAt(1, false);
        if (this.fp != null && this.fp == fp) {
            update();
            return;
        }
        this.fp = fp;
        this.tabbedPane.setSelectedIndex(0);
        if (this.fp != null) {
            update();
        }
    }

    public void update() {
        if (fp == null) return;
        /*
         * wichtig: die set funktionen lösen eine action aus , welche ansonsten
         * wiederum ein updatevent aufrufen würden
         */
        notifyUpdate = false;
        if (!txtName.isFocusOwner()) txtName.setText(fp.getName());
        if (!txtComment.isFocusOwner()) txtComment.setText(fp.getComment());
        if (!txtPassword.isFocusOwner()) txtPassword.setText(fp.getPassword());
        if (!brwSaveTo.isFocusOwner()) brwSaveTo.setText(fp.getDownloadDirectory());
        if (!chbExtract.isFocusOwner()) chbExtract.setSelected(fp.isExtractAfterDownload());
        /* neuzeichnen */
        revalidate();
        notifyUpdate = true;
    }

    public FilePackage getPackage() {
        return fp;
    }

    private void buildGui() {
        tabbedPane = new JTabbedPane();
        tabbedPane.add(createFilePackageInfo(), JDLocale.L("gui.fileinfopanel.packagetab", "Package"));
        tabbedPane.add(createLinkInfo(), JDLocale.L("gui.fileinfopanel.link", "Downloadlink"));
        this.setLayout(new MigLayout("", "[grow]", "[]"));
        this.add(tabbedPane, "grow");
    }

    private JPanel createFilePackageInfo() {
        txtName = new JDTextField();
        txtName.setAutoSelect(true);

        addChangeListener(txtName);
        txtName.setBackground(null);
        brwSaveTo = new ComboBrowseFile("DownloadSaveTo");
        brwSaveTo.setEditable(true);
        brwSaveTo.setFileSelectionMode(JDFileChooser.DIRECTORIES_ONLY);
        brwSaveTo.setText(JDUtilities.getConfiguration().getDefaultDownloadDirectory());
        brwSaveTo.addActionListener(this);

        brwSaveTo.getInput().setBackground(null);
        txtPassword = new JDTextField();
        addChangeListener(txtPassword);

        txtComment = new JDTextField();
        addChangeListener(txtComment);

        txtComment.setBackground(null);
        txtPassword.setBackground(null);
        chbExtract = new JCheckBox(JDLocale.L("gui.fileinfopanel.packagetab.chb.extractAfterdownload", "Extract"));
        chbExtract.setSelected(true);
        chbExtract.setHorizontalTextPosition(SwingConstants.LEFT);
        chbExtract.addActionListener(this);

        chbUseSubdirectory = new JCheckBox(JDLocale.L("gui.linkgrabber.packagetab.chb.useSubdirectory", "Use Subdirectory"));
        chbUseSubdirectory.setSelected(JDUtilities.getConfiguration().getBooleanProperty(Configuration.PARAM_USE_PACKETNAME_AS_SUBFOLDER, false));
        chbUseSubdirectory.setHorizontalTextPosition(SwingConstants.LEFT);
        chbUseSubdirectory.addActionListener(this);

        progressBarFilePackage = new MultiProgressBar();
        panel = new JPanel();
        panel.setLayout(new MigLayout("ins 10, wrap 3", "[]10[grow,fill][]", "[]5[]5[]5[]"));
        panel.add(progressBarFilePackage, "spanx,growx,pushx");
        panel.add(new JLabel(JDLocale.L("gui.fileinfopanel.packagetab.lbl.name", "Paketname")));
        panel.add(txtName, "span 2");
        panel.add(new JLabel(JDLocale.L("gui.fileinfopanel.packagetab.lbl.saveto", "Speichern unter")));
        panel.add(brwSaveTo.getInput(), "gapright 10, growx");
        panel.add(brwSaveTo.getButton(), "pushx,growx");
        panel.add(new JLabel(JDLocale.L("gui.fileinfopanel.packagetab.lbl.password", "Archivpasswort")));
        panel.add(txtPassword, " gapright 10, growx");
        panel.add(chbExtract, "alignx right");
        panel.add(new JLabel(JDLocale.L("gui.fileinfopanel.packagetab.lbl.comment", "Kommentar")));
        panel.add(txtComment, "gapright 10, growx");
        panel.add(chbUseSubdirectory, "alignx right");
        return panel;
    }

    private void addChangeListener(final JDTextField txtName2) {
        txtName2.getDocument().addDocumentListener(new DocumentListener() {

            public void changedUpdate(DocumentEvent e) {
                actionPerformed(new ActionEvent(txtName2, 0, null));
            }

            public void insertUpdate(DocumentEvent e) {
                actionPerformed(new ActionEvent(txtName2, 0, null));
            }

            public void removeUpdate(DocumentEvent e) {
                actionPerformed(new ActionEvent(txtName2, 0, null));

            }

        });

    }

    private JPanel createLinkInfo() {
        txtpathlabel = new JDTextField();
        txtpathlabel.setEditable(false);
        progressBarDownloadLink = new MultiProgressBar();
        panel = new JPanel();
        panel.setLayout(new MigLayout("ins 10, wrap 3", "[]10[grow,fill][]", "[]5[]5[]5[]"));
        panel.add(hosterlabel = new JLabel(JDTheme.II("gui.images.sort", 16, 16)), "split 2");
        panel.add(typeicon = new JLabel(JDTheme.II("gui.images.sort", 16, 16)), "");
        typeicon.setText(JDLocale.L("gui.fileinfopanel.linktab.chunks", "Chunks"));
        panel.add(progressBarDownloadLink, "spanx,growx,pushx");
        panel.add(eta = new JLabel(JDLocale.LF("gui.fileinfopanel.linktab.eta", "ETA: %s mm:ss", "0")));
        panel.add(speed = new JLabel(JDLocale.LF("gui.fileinfopanel.linktab.speed", "Speed: %s/s", "0 kb")), "skip,alignx right");

        panel.add(new JLabel(JDLocale.L("gui.fileinfopanel.linktab.saveto", "Save to")));
        panel.add(txtpathlabel, "growx, span 2");
        panel.add(new JLabel(JDLocale.L("gui.fileinfopanel.linktab.url", "URL")));
        panel.add(txtURL=new JDTextField(), "growx, span 2");
        txtURL.setEditable(false);
        panel.add(new JLabel(JDLocale.L("gui.fileinfopanel.linktab.filesize", "Filesize")));
        panel.add(lblSize=new JLabel(""), "growx, span 2");

        return panel;
    }

    public void actionPerformed(ActionEvent e) {
        if (fp == null || !notifyUpdate) return;
        if (e.getSource() == txtName) {
            fp.setName(txtName.getText());
        } else if (e.getSource() == brwSaveTo) {
            fp.setDownloadDirectory(brwSaveTo.getText());
        } else if (e.getSource() == txtComment) {
            fp.setComment(txtComment.getText());
        } else if (e.getSource() == txtPassword) {
            fp.setPassword(txtPassword.getText());
        } else if (e.getSource() == chbExtract) {
            fp.setExtractAfterDownload(chbExtract.isSelected());
        }
        DownloadController.getInstance().fireDownloadLinkUpdate(fp.get(0));
    }

    // @Override
    public void onDisplay() {
        update();
        hidden = false;
        if (updater != null && updater.isAlive()) return;
        updater = new Thread() {
            public void run() {
                while (true && !hidden) {
                    progressBarFilePackage.setMaximums(null);
                    progressBarDownloadLink.setMaximums(null);
                    if (fp != null) {
                        long[] max = new long[fp.getDownloadLinkList().size()];
                        long[] values = new long[fp.getDownloadLinkList().size()];
                        int i = 0;
                        for (DownloadLink dl : fp.getDownloadLinkList()) {
                            max[i] = Math.max(1024, dl.getDownloadSize());
                            values[i] = Math.max(1, dl.getDownloadCurrent());
                            i++;
                        }
                        FilePackageInfo.this.progressBarFilePackage.setMaximums(max);
                        FilePackageInfo.this.progressBarFilePackage.setValues(values);
                    }
                    if (downloadLink != null) {
                        if (downloadLink.getChunksProgress() != null) {
                            long fileSize = downloadLink.getDownloadSize();
                            int chunks = downloadLink.getChunksProgress().length;
                            long part = fileSize / chunks;

                            long[] max = new long[chunks];
                            long[] values = new long[chunks];
                            for (int i = 0; i < chunks; i++) {
                                max[i] = part;
                                values[i] = downloadLink.getChunksProgress()[i] - i * part;
                            }

                            FilePackageInfo.this.progressBarDownloadLink.setMaximums(max);
                            FilePackageInfo.this.progressBarDownloadLink.setValues(values);
                        }else{
                            FilePackageInfo.this.progressBarDownloadLink.setMaximums(new long[]{10});
                            FilePackageInfo.this.progressBarDownloadLink.setValues(new long[]{0});
                        }
                        speed.setText(JDLocale.LF("gui.fileinfopanel.linktab.speed", "Speed: %s/s", Formatter.formatReadable(Math.max(0, downloadLink.getDownloadSpeed()))));
                        if (downloadLink.getDownloadSpeed() <= 0) {
                            eta.setVisible(false);
                        } else {
                            eta.setText(JDLocale.LF("gui.fileinfopanel.linktab.eta", "ETA: %s mm:ss", Formatter.formatSeconds((downloadLink.getDownloadSize() - downloadLink.getDownloadCurrent()) / downloadLink.getDownloadSpeed())));
                            eta.setVisible(true);
                        }
                    }
                    revalidate();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };
        updater.start();
    }

    // @Override
    public void onHide() {
        hidden = true;
        if (updater != null) {
            updater.interrupt();
            updater = null;
        }
        
        PasswordList.addPassword(txtPassword.getText());
        PasswordList.save();
        actionPerformed(new ActionEvent(this.brwSaveTo, 0, null));
        this.progressBarFilePackage.setMaximums(null);
        this.progressBarDownloadLink.setMaximums(null);
        downloadLink = null;
        fp = null;
    }

    public void setDownloadLink(DownloadLink downloadLink) {
        this.tabbedPane.setEnabledAt(1, true);
        this.tabbedPane.setSelectedIndex(1);
        if (this.downloadLink != null && this.downloadLink == downloadLink) { return; }
        this.downloadLink = downloadLink;
        if (downloadLink != null) {
            fp = downloadLink.getFilePackage();
            this.txtpathlabel.setText(downloadLink.getFileOutput());
            this.typeicon.setIcon(downloadLink.getIcon());
            if (downloadLink.getPlugin() != null) {
                this.hosterlabel.setIcon(downloadLink.getPlugin().getHosterIcon());
            } else {
                this.hosterlabel.setIcon(null);
            }

            hosterlabel.setToolTipText(downloadLink.getHost());
            typeicon.setToolTipText(downloadLink.getHost());
            this.lblSize.setText(Formatter.formatReadable(downloadLink.getDownloadSize()));
            if(downloadLink.getLinkType()==DownloadLink.LINKTYPE_NORMAL){
             this.txtURL.setText(downloadLink.getDownloadURL());
            }else{
                this.txtURL.setText("**************");
            }
        }
        if (this.fp != null) {
            update();
        }

    }

}

//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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

package jd.controlling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import jd.config.Configuration;
import jd.config.SubConfiguration;
import jd.controlling.interaction.Interaction;
import jd.controlling.reconnect.Reconnecter;
import jd.event.ControlEvent;
import jd.event.ControlListener;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

/**
 * Dieser Controller verwaltet die downloads. Während StartDownloads.java für
 * die Steuerung eines einzelnen Downloads zuständig ist, ist DownloadWatchdog
 * für die Verwaltung und steuerung der ganzen Download Liste zuständig
 * 
 * @author JD-Team
 * 
 */
public class DownloadWatchDog implements ControlListener, DownloadControllerListener {

    private boolean aborted = false;

    private boolean aborting;

    private HashMap<DownloadLink, SingleDownloadController> DownloadControllers = new HashMap<DownloadLink, SingleDownloadController>();

    private static final Object nostopMark = new Object();
    private static final Object hiddenstopMark = new Object();
    private Object stopMark = nostopMark;

    private HashMap<Class<?>, Integer> activeHosts = new HashMap<Class<?>, Integer>();

    private Logger logger = jd.controlling.JDLogger.getLogger();

    private boolean pause = false;

    private int totalSpeed = 0;

    private Thread WatchDogThread = null;

    private DownloadController dlc = null;

    private Integer activeDownloads = new Integer(0);

    private static DownloadWatchDog INSTANCE;

    public synchronized static DownloadWatchDog getInstance() {
        if (INSTANCE == null) INSTANCE = new DownloadWatchDog();
        return INSTANCE;
    }

    private DownloadWatchDog() {
        dlc = DownloadController.getInstance();
        dlc.addListener(this);
    }

    public void start() {
        stopMark = nostopMark;
        startWatchDogThread();
    }

    public void setStopMark(Object entry) {
        synchronized (stopMark) {
            if (entry == null) entry = nostopMark;
            if (stopMark instanceof DownloadLink) {
                DownloadController.getInstance().fireDownloadLinkUpdate(stopMark);
            } else if (stopMark instanceof FilePackage) {
                DownloadController.getInstance().fireDownloadLinkUpdate(((FilePackage) stopMark).get(0));
            }
            stopMark = entry;
            if (entry instanceof DownloadLink) {
                DownloadController.getInstance().fireDownloadLinkUpdate(entry);
            } else if (entry instanceof FilePackage) {
                DownloadController.getInstance().fireDownloadLinkUpdate(((FilePackage) entry).get(0));
            }
        }
    }

    public void toggleStopMark(Object entry) {
        synchronized (stopMark) {
            if (entry == null || entry == stopMark) {
                setStopMark(nostopMark);
                return;
            }
            setStopMark(entry);
        }
    }

    public Object getStopMark() {
        return stopMark;
    }

    public boolean isStopMark(Object item) {
        return stopMark == item;
    }

    /**
     * Bricht den Watchdog ab. Alle laufenden downloads werden beendet und die
     * downloadliste zurückgesetzt. Diese Funktion blockiert bis alle Downloads
     * erfolgreich abgeborhcen wurden.
     */
    void abort() {
        logger.finer("Breche alle activeLinks ab");
        aborting = true;
        aborted = true;

        ProgressController progress = new ProgressController("Termination", activeDownloads);
        progress.setStatusText("Stopping all downloads " + activeDownloads);
        ArrayList<DownloadLink> al = new ArrayList<DownloadLink>();

        synchronized (DownloadControllers) {
            Vector<SingleDownloadController> cons = new Vector<SingleDownloadController>(DownloadControllers.values());
            for (SingleDownloadController singleDownloadController : cons) {
                al.add(singleDownloadController.abortDownload().getDownloadLink());
            }
            DownloadController.getInstance().fireDownloadLinkUpdate(al);
            boolean check = true;
            // Warteschleife bis alle activelinks abgebrochen wurden
            logger.finer("Warten bis alle activeLinks abgebrochen wurden.");

            while (true) {
                progress.setStatusText("Stopping all downloads " + activeDownloads);
                check = true;
                Vector<DownloadLink> links = new Vector<DownloadLink>(DownloadControllers.keySet());
                for (DownloadLink link : links) {
                    if (link.getLinkStatus().isPluginActive()) {
                        check = false;
                        break;
                    }
                }
                if (check) {
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }
        DownloadController.getInstance().fireDownloadLinkUpdate(al);
        progress.finalize();
        logger.finer("Stopped Downloads");
        clearDownloadListStatus();
        aborting = false;
    }

    /**
     * Setzt den Status der Downloadliste zurück. zB. bei einem Abbruch
     */
    private void clearDownloadListStatus() {
        synchronized (DownloadControllers) {
            Vector<DownloadLink> links = new Vector<DownloadLink>(DownloadControllers.keySet());
            for (DownloadLink link : links) {
                this.deactivateDownload(link);
            }
        }
        PluginForHost.resetStatics();
        Vector<FilePackage> fps;
        Vector<DownloadLink> links;
        fps = dlc.getPackages();
        synchronized (fps) {
            for (FilePackage filePackage : fps) {
                links = filePackage.getDownloadLinks();
                for (int i = 0; i < links.size(); i++) {
                    if (!links.elementAt(i).getLinkStatus().hasStatus(LinkStatus.FINISHED)) {
                        links.elementAt(i).getLinkStatus().setStatusText(null);
                        links.elementAt(i).setAborted(false);
                        links.elementAt(i).getLinkStatus().setStatus(LinkStatus.TODO);
                        links.elementAt(i).getLinkStatus().resetWaitTime();
                    }
                }
            }
        }
        DownloadController.getInstance().fireGlobalUpdate();
    }

    public void controlEvent(ControlEvent event) {
        if (event.getID() == ControlEvent.CONTROL_PLUGIN_INACTIVE && event.getSource() instanceof PluginForHost) {
            this.deactivateDownload(((SingleDownloadController) event.getParameter()).getDownloadLink());
        }
    }

    public int ActiveDownloadControllers() {
        return DownloadControllers.keySet().size();
    }

    /**
     * Zählt die Downloads die bereits über das Hostplugin laufen
     * 
     * @param plugin
     * @return Anzahl der downloads über das plugin
     */
    private int activeDownloadsbyHosts(PluginForHost plugin) {
        synchronized (this.activeHosts) {
            if (activeHosts.containsKey(plugin.getClass())) { return activeHosts.get(plugin.getClass()); }
        }
        return 0;
    }

    private void activateDownload(DownloadLink link, SingleDownloadController con) {
        synchronized (DownloadControllers) {
            if (DownloadControllers.containsKey(link)) return;
            DownloadControllers.put(link, con);
            synchronized (this.activeDownloads) {
                this.activeDownloads++;
            }
        }
        Class<?> cl = link.getPlugin().getClass();
        synchronized (this.activeHosts) {
            if (activeHosts.containsKey(cl)) {
                int count = activeHosts.get(cl);
                activeHosts.put(cl, count + 1);
            } else {
                activeHosts.put(cl, 1);
            }
        }
    }

    private void deactivateDownload(DownloadLink link) {
        synchronized (DownloadControllers) {
            if (!DownloadControllers.containsKey(link)) {
                logger.severe("Link not in ControllerList!");
                return;
            }
            DownloadControllers.remove(link);
            synchronized (this.activeDownloads) {
                this.activeDownloads--;
            }
        }
        Class<?> cl = link.getPlugin().getClass();
        synchronized (this.activeHosts) {
            if (activeHosts.containsKey(cl)) {
                int count = activeHosts.get(cl);
                if (count - 1 < 0) {
                    logger.severe("WatchDog Counter MissMatch!!");
                    activeHosts.remove(cl);
                } else
                    activeHosts.put(cl, count - 1);
            } else
                logger.severe("WatchDog Counter MissMatch!!");
        }
    }

    /**
     * Liefert den nächsten DownloadLink
     * 
     * @return Der nächste DownloadLink oder null
     */
    public DownloadLink getNextDownloadLink() {
        if (this.reachedStopMark()) return null;
        DownloadLink nextDownloadLink = null;
        DownloadLink returnDownloadLink = null;
        try {
            for (FilePackage filePackage : dlc.getPackages()) {
                for (Iterator<DownloadLink> it2 = filePackage.getDownloadLinks().iterator(); it2.hasNext();) {
                    nextDownloadLink = it2.next();
                    // Setzt die Wartezeit zurück
                    if (!nextDownloadLink.getLinkStatus().isPluginActive() && nextDownloadLink.getLinkStatus().hasStatus(LinkStatus.DOWNLOADINTERFACE_IN_PROGRESS)) {
                        nextDownloadLink.reset();
                        nextDownloadLink.getLinkStatus().setStatus(LinkStatus.TODO);
                    }
                    if (nextDownloadLink.isEnabled() && !nextDownloadLink.getLinkStatus().hasStatus(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE)) {
                        if (nextDownloadLink.getPlugin().ignoreHosterWaittime(nextDownloadLink) || nextDownloadLink.getPlugin().getRemainingHosterWaittime() <= 0) {
                            if (!isDownloadLinkActive(nextDownloadLink)) {
                                // if (!nextDownloadLink.isAborted()) {
                                if (!nextDownloadLink.getLinkStatus().isPluginActive()) {

                                    if (nextDownloadLink.getLinkStatus().isStatus(LinkStatus.TODO)) {

                                        int maxPerHost = getSimultanDownloadNumPerHost();
                                        if (maxPerHost == 0) maxPerHost = Integer.MAX_VALUE;

                                        if (activeDownloadsbyHosts(nextDownloadLink.getPlugin()) < (nextDownloadLink.getPlugin()).getMaxSimultanDownloadNum(nextDownloadLink) && activeDownloadsbyHosts(nextDownloadLink.getPlugin()) < maxPerHost && nextDownloadLink.getPlugin().getWrapper().usePlugin()) {
                                            if (returnDownloadLink == null) {
                                                returnDownloadLink = nextDownloadLink;
                                            } else {
                                                if (nextDownloadLink.getPriority() > returnDownloadLink.getPriority()) returnDownloadLink = nextDownloadLink;
                                            }
                                        }

                                    }
                                }
                            }
                            // }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // jd.controlling.JDLogger.getLogger().log(java.util.logging.Level.
            // SEVERE,"Exception occured",e);
            // Fängt concurrentmodification Exceptions ab
        }
        return returnDownloadLink;
    }

    /**
     * Gibt die Configeinstellung zurück, wieviele simultane Downloads der user
     * erlaubt hat
     * 
     * @return
     */
    public int getSimultanDownloadNum() {
        return SubConfiguration.getConfig("DOWNLOAD").getIntegerProperty(Configuration.PARAM_DOWNLOAD_MAX_SIMULTAN, 2);
    }

    /**
     * Gibt die Configeinstellung zurück, wieviele simultane Downloads der user
     * pro Hoster erlaubt hat
     * 
     * @return
     */
    public int getSimultanDownloadNumPerHost() {
        return SubConfiguration.getConfig("DOWNLOAD").getIntegerProperty(Configuration.PARAM_DOWNLOAD_MAX_SIMULTAN_PER_HOST, 0);
    }

    /**
     * @return the totalSpeed
     */
    public int getTotalSpeed() {
        return totalSpeed;
    }

    public boolean isAborted() {
        if (WatchDogThread == null) return false;
        return !WatchDogThread.isAlive();
    }

    public boolean isAlive() {
        if (WatchDogThread == null) return false;
        return WatchDogThread.isAlive();
    }

    boolean isDownloadLinkActive(DownloadLink nextDownloadLink) {
        synchronized (DownloadControllers) {
            return DownloadControllers.containsKey(nextDownloadLink);
        }
    }

    public void pause(boolean value) {
        pause = value;
        if (value) {
            logger.info("Pause enabled: Reduced Downloadspeed to 1 kb/s");
        } else {
            logger.info("Pause disabled");
        }

    }

    private synchronized void startWatchDogThread() {
        if (this.WatchDogThread == null || !this.WatchDogThread.isAlive()) {
            WatchDogThread = new Thread() {
                public void run() {
                    JDUtilities.getController().addControlListener(INSTANCE);
                    this.setName("DownloadWatchDog");
                    Vector<DownloadLink> links;
                    ArrayList<DownloadLink> updates = new ArrayList<DownloadLink>();
                    Vector<FilePackage> fps;
                    DownloadLink link;
                    LinkStatus linkStatus;
                    boolean hasWaittimeLinks;
                    boolean hasInProgressLinks;
                    boolean hasTempDisabledLinks;
                    aborted = false;
                    int stopCounter = 5;
                    int currentTotalSpeed = 0;
                    int inProgress = 0;
                    Vector<DownloadLink> removes = new Vector<DownloadLink>();
                    while (aborted != true) {

                        hasWaittimeLinks = false;
                        hasInProgressLinks = false;
                        hasTempDisabledLinks = false;

                        fps = dlc.getPackages();
                        currentTotalSpeed = 0;
                        inProgress = 0;
                        updates.clear();
                        try {

                            for (FilePackage filePackage : fps) {
                                links = filePackage.getDownloadLinks();

                                for (int i = 0; i < links.size(); i++) {
                                    link = links.elementAt(i);
                                    linkStatus = link.getLinkStatus();
                                    if (!link.isEnabled() && link.getLinkType() == DownloadLink.LINKTYPE_JDU && linkStatus.getTotalWaitTime() <= 0) {

                                        removes.add(link);
                                        continue;
                                    }
                                    if (linkStatus.hasStatus(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE)) {

                                        if (linkStatus.getRemainingWaittime() == 0) {
                                            // link.setEnabled(true);
                                            linkStatus.reset();
                                            // updates.add(link);
                                        }
                                        hasTempDisabledLinks = true;

                                    }

                                    // Link mit Wartezeit in der queue
                                    if (link.isEnabled() && linkStatus.hasStatus(LinkStatus.ERROR_IP_BLOCKED) && !linkStatus.hasStatus(LinkStatus.PLUGIN_IN_PROGRESS)) {
                                        if (linkStatus.getRemainingWaittime() == 0) {
                                            // reaktiviere Downloadlink
                                            linkStatus.reset();

                                        }

                                    }
                            
                                    if (linkStatus.getRemainingWaittime() > 0) {
                                        hasWaittimeLinks = true;
                                        updates.add(link);
                                    }
                                    if (link.isEnabled() && linkStatus.isPluginActive()) {
                                        hasInProgressLinks = true;
                                    }
                                    if (link.isEnabled() && linkStatus.hasStatus(LinkStatus.DOWNLOADINTERFACE_IN_PROGRESS)) {
                                        inProgress++;
                                        currentTotalSpeed += link.getDownloadSpeed();
                                    }

                                }
                            }
                            if (removes.size() > 0) {
                                for (DownloadLink dl : removes) {
                                    dl.getFilePackage().remove(dl);
                                }
                                removes.clear();
                            }

                            Reconnecter.doReconnectIfRequested();
                            if (inProgress > 0) {
                                fps = dlc.getPackages();

                                for (FilePackage filePackage : fps) {

                                    Iterator<DownloadLink> iter = filePackage.getDownloadLinks().iterator();
                                    int maxspeed = SubConfiguration.getConfig("DOWNLOAD").getIntegerProperty(Configuration.PARAM_DOWNLOAD_MAX_SPEED, 0) * 1024;
                                    if (maxspeed == 0) {
                                        maxspeed = Integer.MAX_VALUE;
                                    }
                                    int overhead = maxspeed - currentTotalSpeed;

                                    totalSpeed = currentTotalSpeed;

                                    DownloadLink element;
                                    while (iter.hasNext()) {
                                        element = iter.next();
                                        if (element.getLinkStatus().hasStatus(LinkStatus.DOWNLOADINTERFACE_IN_PROGRESS)) {

                                            element.setSpeedLimit(element.getDownloadSpeed() + overhead / inProgress);

                                        }
                                    }
                                }
                            } else {
                                totalSpeed = 0;
                            }
                            if (updates.size() > 0) {
                                DownloadController.getInstance().fireDownloadLinkUpdate(updates);
                            }
                            int ret = 0;
                            if (Interaction.areInteractionsInProgress() && activeDownloads < getSimultanDownloadNum() && !pause) {
                                if (!reachedStopMark()) {
                                    // System.out.println("stopmarke nicht");
                                    ret = setDownloadActive();
                                } else {
                                    System.out.println("stopmarke!!");
                                }
                            }
                            if (ret == 0) {

                                if (pause && !hasInProgressLinks || !hasTempDisabledLinks && !hasInProgressLinks && !hasWaittimeLinks && getNextDownloadLink() == null && activeDownloads == 0) {
                                    stopCounter--;
                                    // System.out.println("stop?");
                                    if (stopCounter == 0) {
                                        totalSpeed = 0;
                                        break;
                                    }

                                }
                            }
                        } catch (Exception e) {
                            JDLogger.exception(e);
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                    }
                    aborted = true;
                    while (aborting) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                    }
                    JDUtilities.getController().fireControlEvent(new ControlEvent(this, ControlEvent.CONTROL_ALL_DOWNLOADS_FINISHED, this));
                    JDUtilities.getController().removeControlListener(INSTANCE);
                    Interaction.handleInteraction(Interaction.INTERACTION_ALL_DOWNLOADS_FINISHED, this);
                }
            };
            WatchDogThread.start();
        }
    }

    /**
     * Aktiviert solange neue Downloads, bis die Maxmalanzahl erreicht ist oder
     * die Liste zueende ist
     * 
     * @return
     */
    private int setDownloadActive() {
        DownloadLink dlink;
        int ret = 0;
        while (activeDownloads < getSimultanDownloadNum()) {
            dlink = getNextDownloadLink();
            if (dlink == null) {
                break;
            }
            if (dlink != getNextDownloadLink()) {
                break;
            }
            if (reachedStopMark()) return ret;
            startDownloadThread(dlink);
            ret++;
        }
        return ret;
    }

    /**
     * @param totalSpeed
     *            the totalSpeed to set
     */
    public void setTotalSpeed(int totalSpeed) {
        this.totalSpeed = totalSpeed;
    }

    private boolean reachedStopMark() {
        synchronized (stopMark) {
            if (stopMark == hiddenstopMark) return true;
            if (stopMark instanceof DownloadLink) {
                if (((DownloadLink) stopMark).isEnabled() && (((DownloadLink) stopMark).getLinkStatus().isPluginActive() || ((DownloadLink) stopMark).getLinkStatus().hasStatus(LinkStatus.FINISHED))) return true;
                return false;
            }
            if (stopMark instanceof FilePackage) {
                for (DownloadLink dl : ((FilePackage) stopMark).getDownloadLinks()) {
                    if (dl.isEnabled() && (!dl.getLinkStatus().isPluginActive() || !dl.getLinkStatus().hasStatus(LinkStatus.FINISHED))) return false;
                }
                return true;
            }
            return false;
        }
    }

    private void startDownloadThread(DownloadLink dlink) {
        Interaction.handleInteraction(Interaction.INTERACTION_BEFORE_DOWNLOAD, dlink);
        SingleDownloadController download = new SingleDownloadController(dlink);
        logger.info("Start new Download: " + dlink.getHost());
        dlink.getLinkStatus().setInProgress(true);
        this.activateDownload(dlink, download);
        download.start();

    }

    public void onDownloadControllerEvent(DownloadControllerEvent event) {
        switch (event.getID()) {
        case DownloadControllerEvent.REMOVE_FILPACKAGE:
        case DownloadControllerEvent.REMOVE_DOWNLOADLINK:
            synchronized (stopMark) {
                if (this.stopMark != null && this.stopMark == event.getParameter()) setStopMark(hiddenstopMark);
            }
            break;
        }

    }
}

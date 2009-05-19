package jd.gui.userio.dialog;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JDialog;
import javax.swing.JLabel;

import jd.config.SubConfiguration;
import jd.gui.skins.simple.GuiRunnable;
import jd.gui.skins.simple.SimpleGUI;
import jd.gui.skins.simple.SimpleGuiConstants;
import jd.nutils.Formatter;
import jd.utils.JDLocale;
import jd.utils.JDTheme;

public abstract class JCountdownDialog extends JDialog {

    private static final long serialVersionUID = 8114522313158766965L;
  
    protected Thread countdownThread;
    protected int countdown;

    protected JLabel countDownLabel;

    public JCountdownDialog(SimpleGUI currentgui) {
        super(currentgui);
        this.countDownLabel = new JLabel();
        countDownLabel.setIcon(JDTheme.II("gui.images.cancel", 16, 16));
        countDownLabel.setToolTipText(JDLocale.L("gui.dialog.countdown.tooltip", "This dialog closes after a certain time. Click here to stop the countdown"));
        countDownLabel.addMouseListener(new MouseListener() {

            public void mouseClicked(MouseEvent e) {
                interrupt();
                countDownLabel.removeMouseListener(this);

            }

            public void mouseEntered(MouseEvent e) {
                // TODO Auto-generated method stub

            }

            public void mouseExited(MouseEvent e) {
                // TODO Auto-generated method stub

            }

            public void mousePressed(MouseEvent e) {
                // TODO Auto-generated method stub

            }

            public void mouseReleased(MouseEvent e) {
                // TODO Auto-generated method stub

            }
        });
        setTitle(JDLocale.L("gui.captchaWindow.askForInput", "Please enter..."));
    }

    public void interrupt() {
        if (countdownThread != null) {
            countdownThread.interrupt();
            countdownThread = null;
            countDownLabel.setEnabled(false);
        }
    }

    protected abstract void onCountdown();

    protected void countdown(int time) {
        this.countdown = time;
        countdownThread = new Thread() {

            // @Override
            public void run() {

                while (!isVisible()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                int c = countdown;

                while (--c >= 0) {
                    if (!isVisible()) return;
                    if (countdownThread == null) return;
                    final String left = Formatter.formatSeconds(c);

                    new GuiRunnable<Object>() {

                        // @Override
                        public Object runSave() {
                            countDownLabel.setText(JDLocale.LF("gui.dialogs.countdown.label", "%s sec", left));
                            return null;
                        }

                    }.start();
                    // if (c <= 3)
                    // JDSounds.P("sound.captcha.onCaptchaInputEmergency");

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (countdown < 0) return;
                    if (!isVisible()) return;

                }
                if (countdown < 0) return;
                if (!this.isInterrupted()) onCountdown();

            }

        };

        countdownThread.start();

    }
}

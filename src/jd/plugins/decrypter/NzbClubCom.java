package jd.plugins.decrypter;

import java.io.File;
import java.util.ArrayList;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.http.requests.GetRequest;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.Plugin;
import jd.plugins.PluginForDecrypt;

import org.appwork.utils.Application;
import org.appwork.utils.IO;
import org.appwork.utils.StringUtils;
import org.appwork.utils.net.URLHelper;
import org.appwork.utils.os.CrossSystem;

@DecrypterPlugin(revision = "$Revision: 26321 $", interfaceVersion = 3, names = { "nzbclub.com" }, urls = { "https?://[\\w\\.]*nzbclub.com/nzb_view/\\d+" }, flags = { 0 })
public class NzbClubCom extends PluginForDecrypt {

    @Override
    public Boolean siteTesterDisabled() {
        return Boolean.TRUE;
    }

    public NzbClubCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        URLConnectionAdapter con = null;
        File nzbFile = null;
        try {
            final String nzbID = new Regex(param.getCryptedUrl(), "/(\\d+)$").getMatch(0);
            br.setFollowRedirects(true);
            br.getPage(param.getCryptedUrl());
            final String pkgName = br.getRegex("ContentPlaceHolder1_ui_titlesmall\">(.*?)<").getMatch(0);
            final Request request = new GetRequest(URLHelper.parseLocation(br.getRequest().getURL(), "/nzb_get/" + nzbID));
            request.getHeaders().put("Accept-Encoding", "identity");
            con = br.openRequestConnection(request);
            final String fileName;
            if (con.isContentDisposition()) {
                final String tmp = Plugin.getFileNameFromDispositionHeader(con);
                if (StringUtils.endsWithCaseInsensitive(tmp, ".nzb")) {
                    fileName = tmp;
                } else {
                    fileName = tmp + ".nzb";
                }
            } else {
                fileName = System.currentTimeMillis() + ".nzb";
            }
            final String contentType = con.getContentType();
            if (StringUtils.containsIgnoreCase(contentType, "nzb") && con.isOK()) {
                nzbFile = Application.getTempResource("container/" + CrossSystem.alleviatePathParts(fileName));
                IO.secureWrite(nzbFile, IO.readStream(-1, con.getInputStream()));
                final DownloadLink link = new DownloadLink(null, nzbFile.getName(), null, URLHelper.createURL(nzbFile.toURI().toURL().toExternalForm()).toExternalForm(), true);
                link.setContainerUrl(param.getCryptedUrl());
                if (pkgName != null) {
                    final FilePackage fp = FilePackage.getInstance();
                    fp.setName(pkgName);
                    fp.add(link);
                }
                ret.add(link);
            } else {
                br.followConnection();
            }
        } finally {
            if (con != null) {
                con.disconnect();
            }
            if (nzbFile != null) {
                if (ret.size() == 0) {
                    nzbFile.delete();
                } else {
                    nzbFile.deleteOnExit();
                }
            }
        }
        return ret;
    }
}

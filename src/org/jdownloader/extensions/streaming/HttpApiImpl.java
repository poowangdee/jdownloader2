package org.jdownloader.extensions.streaming;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;

import jd.plugins.DownloadLink;

import org.appwork.exceptions.WTFException;
import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.net.protocol.http.HTTPConstants.ResponseCode;
import org.appwork.remoteapi.RemoteAPIException;
import org.appwork.utils.Application;
import org.appwork.utils.Files;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.ImageProvider.ImageProvider;
import org.appwork.utils.images.IconIO;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.net.HTTPHeader;
import org.appwork.utils.net.httpserver.handler.HttpRequestHandler;
import org.appwork.utils.net.httpserver.requests.GetRequest;
import org.appwork.utils.net.httpserver.requests.HeadRequest;
import org.appwork.utils.net.httpserver.requests.PostRequest;
import org.appwork.utils.net.httpserver.responses.HttpResponse;
import org.jdownloader.extensions.ExtensionController;
import org.jdownloader.extensions.extraction.Archive;
import org.jdownloader.extensions.extraction.ExtractionExtension;
import org.jdownloader.extensions.extraction.bindings.downloadlink.DownloadLinkArchiveFactory;
import org.jdownloader.extensions.streaming.dataprovider.PipeStreamingInterface;
import org.jdownloader.extensions.streaming.dataprovider.rar.PartFileDataProvider;
import org.jdownloader.extensions.streaming.dataprovider.rar.RarArchiveDataProvider;
import org.jdownloader.extensions.streaming.dlna.DLNATransferMode;
import org.jdownloader.extensions.streaming.dlna.DLNATransportConstants;
import org.jdownloader.extensions.streaming.dlna.profiles.Profile;
import org.jdownloader.extensions.streaming.dlna.profiles.image.JPEGImage;
import org.jdownloader.extensions.streaming.dlna.profiles.video.AbstractAudioVideoProfile;
import org.jdownloader.extensions.streaming.mediaarchive.MediaItem;
import org.jdownloader.extensions.streaming.mediaarchive.ProfileMatch;
import org.jdownloader.extensions.streaming.mediaarchive.VideoMediaItem;
import org.jdownloader.extensions.streaming.rarstream.RarStreamer;
import org.jdownloader.extensions.streaming.upnp.MediaServer;
import org.jdownloader.extensions.streaming.upnp.RendererDevice;
import org.jdownloader.logging.LogController;

public class HttpApiImpl implements HttpRequestHandler {

    private HashMap<String, StreamingInterface> interfaceMap = new HashMap<String, StreamingInterface>();
    private StreamingExtension                  extension;
    private LogSource                           logger;
    private MediaServer                         mediaServer;

    public HttpApiImpl(StreamingExtension extension, MediaServer mediaServer) {
        this.extension = extension;
        this.mediaServer = mediaServer;
        logger = LogController.getInstance().getLogger(getClass().getName());
        Profile.init();

    }

    public void addHandler(String name, StreamingInterface rarStreamer) {
        interfaceMap.put(name, rarStreamer);
    }

    public StreamingInterface getStreamingInterface(String name) {
        return interfaceMap.get(name);
    }

    public static final int DLNA_ORG_FLAG_SENDER_PACED               = (1 << 31);
    public static final int DLNA_ORG_FLAG_TIME_BASED_SEEK            = (1 << 30);
    public static final int DLNA_ORG_FLAG_BYTE_BASED_SEEK            = (1 << 29);
    public static final int DLNA_ORG_FLAG_PLAY_CONTAINER             = (1 << 28);
    public static final int DLNA_ORG_FLAG_S0_INCREASE                = (1 << 27);
    public static final int DLNA_ORG_FLAG_SN_INCREASE                = (1 << 26);
    public static final int DLNA_ORG_FLAG_RTSP_PAUSE                 = (1 << 25);
    public static final int DLNA_ORG_FLAG_STREAMING_TRANSFER_MODE    = (1 << 24);
    public static final int DLNA_ORG_FLAG_INTERACTIVE_TRANSFERT_MODE = (1 << 23);
    public static final int DLNA_ORG_FLAG_BACKGROUND_TRANSFERT_MODE  = (1 << 22);
    public static final int DLNA_ORG_FLAG_CONNECTION_STALL           = (1 << 21);
    public static final int DLNA_ORG_FLAG_DLNA_V15                   = (1 << 20);

    @Override
    public boolean onGetRequest(GetRequest request, HttpResponse response) {

        String[] params = new Regex(request.getRequestedPath(), "^/stream/([^\\/]+)/([^\\/]+)/?(.*)$").getRow(0);
        if (params == null) return false;
        String deviceid = null;
        try {
            deviceid = URLDecoder.decode(params[0], "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        }
        String id = params[1];
        String subpath = params[2];

        try {
            if (".albumart.jpeg_tn".equals(subpath)) {
                onAlbumArtRequest(request, response, id, JPEGImage.JPEG_TN);
                return true;

            } else if (".albumart.jpeg_sm".equals(subpath)) {
                onAlbumArtRequest(request, response, id, JPEGImage.JPEG_SM);
                return true;

            }
            // can be null if this has been a playto from downloadlist or linkgrabber
            MediaItem mediaItem = (MediaItem) extension.getMediaArchiveController().getItemById(id);
            DownloadLink dlink = extension.getLinkById(id);
            if (dlink == null) { throw new WTFException("Link null"); }

            final DownloadLink link = dlink;
            StreamingInterface streamingInterface = null;
            streamingInterface = interfaceMap.get(id);
            boolean archiveIsOpen = true;
            if (streamingInterface == null) {
                ExtractionExtension archiver = (ExtractionExtension) ExtensionController.getInstance().getExtension(ExtractionExtension.class)._getExtension();

                DownloadLinkArchiveFactory fac = new DownloadLinkArchiveFactory(dlink);
                Archive archive = archiver.getArchiveByFactory(fac);
                if (archive != null) {

                    streamingInterface = new PipeStreamingInterface(null, new RarArchiveDataProvider(archive, subpath, new PartFileDataProvider(extension.getDownloadLinkDataProvider())));
                    // Thread.sleep(5000);
                    addHandler(id, streamingInterface);

                } else {

                    streamingInterface = new PipeStreamingInterface(dlink, extension.getDownloadLinkDataProvider());
                    addHandler(id, streamingInterface);

                }
            }
            System.out.println(dlink);

            String format = Files.getExtension(dlink.getName());
            if (!StringUtils.isEmpty(subpath)) {
                format = Files.getExtension(subpath);
            }
            // seeking

            RendererDevice callingDevice = getCaller(deviceid, request);

            logger.info("Call from " + callingDevice);
            String dlnaFeatures = null;
            // we should redirect the contenttype from the plugins by default.
            String ct = org.jdownloader.extensions.streaming.dlna.Extensions.getType(format) + "/" + format;
            String acceptRanges = "bytes";
            if (mediaItem != null) {

                boolean isTranscodeRequired = false;
                Profile dlnaProfile = callingDevice.getBestProfile(mediaItem);
                if (dlnaProfile == null) {
                    isTranscodeRequired = true;
                    dlnaProfile = callingDevice.getBestTranscodeProfile(mediaItem);
                }
                dlnaFeatures = "DLNA.ORG_PN=" + callingDevice.createDlnaOrgPN(dlnaProfile, mediaItem) + ";DLNA.ORG_OP=" + callingDevice.createDlnaOrgOP(dlnaProfile, mediaItem) + ";DLNA.ORG_FLAGS=" + callingDevice.createDlnaOrgFlags(dlnaProfile, mediaItem);
                acceptRanges = callingDevice.createHeaderAcceptRanges(dlnaProfile, mediaItem);
                ct = callingDevice.createContentType(dlnaProfile, mediaItem);

            }

            response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_RESPONSE_CONTENT_TYPE, ct));

            if (dlnaFeatures != null) response.getResponseHeaders().add(new HTTPHeader("ContentFeatures.DLNA.ORG", dlnaFeatures));
            response.getResponseHeaders().add(new HTTPHeader(DLNATransportConstants.HEADER_TRANSFERMODE, getTransferMode(request, DLNATransferMode.STREAMING)));
            response.getResponseHeaders().add(new HTTPHeader("Accept-Ranges", acceptRanges));
            if (streamingInterface instanceof RarStreamer) {
                while (((RarStreamer) streamingInterface).getExtractionThread() == null && ((RarStreamer) streamingInterface).getException() == null) {
                    Thread.sleep(100);
                }

                if (((RarStreamer) streamingInterface).getException() != null) {
                    response.setResponseCode(ResponseCode.ERROR_BAD_REQUEST);
                    response.getOutputStream();
                    response.closeConnection();
                    return true;

                }
            }

            long length;
            if (request instanceof HeadRequest) {
                System.out.println("HEAD " + request.getRequestHeaders());
                response.setResponseCode(ResponseCode.SUCCESS_OK);
                length = streamingInterface.getFinalFileSize();
                if (length > 0) response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_REQUEST_CONTENT_LENGTH, length + ""));

                response.getOutputStream();
                response.closeConnection();
                return true;
            } else if (request instanceof GetRequest) {
                System.out.println("GET " + request.getRequestHeaders());

                try {

                    new StreamingThread(response, request, streamingInterface).run();

                } catch (final Throwable e) {
                    if (e instanceof RemoteAPIException) throw (RemoteAPIException) e;
                    throw new RemoteAPIException(e);
                }
            }
        } catch (final Throwable e) {
            logger.log(e);
            if (e instanceof RemoteAPIException) {

            throw (RemoteAPIException) e; }
            throw new RemoteAPIException(e);
        } finally {
            System.out.println("Resp: " + response.getResponseHeaders().toString());
        }
        return false;
    }

    private RendererDevice getCaller(String deviceid, GetRequest request) {

        // try to find user agent matches

        return mediaServer.getDeviceManager().findDevice(deviceid, request.getRemoteAddress().get(0), request.getRequestHeaders());
    }

    public void onAlbumArtRequest(GetRequest request, HttpResponse response, String id, JPEGImage profile) throws IOException {
        MediaItem item = (MediaItem) extension.getMediaArchiveController().getItemById(id);
        File path = Application.getResource(item.getThumbnailPath());
        String ct = profile.getMimeType().getLabel();
        String dlnaFeatures = "DLNA.ORG_PN=" + profile.getProfileID();
        if (dlnaFeatures != null) response.getResponseHeaders().add(new HTTPHeader("ContentFeatures.DLNA.ORG", dlnaFeatures));
        response.getResponseHeaders().add(new HTTPHeader(DLNATransportConstants.HEADER_TRANSFERMODE, getTransferMode(request, DLNATransferMode.INTERACTIVE)));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(IconIO.getScaledInstance(ImageProvider.read(path), profile.getWidth().getMax(), profile.getHeight().getMax()), "jpeg", baos);
        baos.close();
        response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_REQUEST_CONTENT_LENGTH, baos.size() + ""));
        response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_REQUEST_CONTENT_TYPE, ct));
        response.getResponseHeaders().add(new HTTPHeader("Accept-Ranges", "none"));
        response.setResponseCode(ResponseCode.SUCCESS_OK);

        // JPegImage.JPEG_TN
        response.getOutputStream().write(baos.toByteArray());

        response.closeConnection();
    }

    private String getTransferMode(GetRequest request, String def) {
        HTTPHeader head = request.getRequestHeaders().get(DLNATransportConstants.HEADER_TRANSFERMODE);
        String ret = null;
        if (head != null && StringUtils.isNotEmpty(head.getValue())) {
            ret = DLNATransferMode.valueOf(head.getValue());
        }
        return ret != null ? ret : def;
    }

    private List<ProfileMatch> findProfile(MediaItem mediaItem) {
        if (mediaItem == null) return null;
        ArrayList<ProfileMatch> ret = new ArrayList<ProfileMatch>();
        logger.info("find DLNA Profile: " + mediaItem.getDownloadLink());
        for (Profile p : Profile.ALL_PROFILES) {
            if (mediaItem instanceof VideoMediaItem) {
                VideoMediaItem video = (VideoMediaItem) mediaItem;
                if (p instanceof AbstractAudioVideoProfile) {
                    ProfileMatch match = video.matches((AbstractAudioVideoProfile) p);

                    if (match != null) {
                        logger.info(match.toString());
                        ret.add(match);
                    }

                }
            }

        }
        return ret;
    }

    @Override
    public boolean onPostRequest(PostRequest request, HttpResponse response) {
        return false;
    }

}

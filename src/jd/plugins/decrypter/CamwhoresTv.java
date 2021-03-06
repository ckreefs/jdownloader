//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "camwhores.tv" }, urls = { "https?://(?:www\\.)?camwhores(tv)?\\.(?:tv|video|biz|sc|io|adult|cc|co|org)/videos/(?:\\d+/[a-z0-9\\-]+/|private/[a-z0-9\\-]+/)" })
public class CamwhoresTv extends PornEmbedParser {
    public CamwhoresTv(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    /* Porn_plugin */
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        this.br.setCookiesExclusive(true);
        String parameter = param.toString();
        getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        } else if (StringUtils.containsIgnoreCase(br.getRedirectLocation(), "cwcams.com/landing")) {
            return decryptedLinks;
        } else if (StringUtils.containsIgnoreCase(br.getRedirectLocation(), "de.stripchat.com")) {
            return decryptedLinks;
        }
        br.followRedirect();
        final String filename = br.getRegex("<title>([^<>\"]*?)</title>").getMatch(0);
        decryptedLinks.addAll(findEmbedUrls(filename));
        if (decryptedLinks.size() == 0) {
            String id = new Regex(parameter, "/videos/(\\d+)").getMatch(0);
            if (id == null) {
                logger.info("Failed to find videoid, probably private video");
                final String filename_url = new Regex(parameter, "([^/]+/)$").getMatch(0);
                /*
                 * Private videos do not contain videoID inside URL but we can usually find the original URL containing that ID inside html.
                 */
                id = br.getRegex("https?://[^/]+/videos/(\\d+)/" + Pattern.compile(filename_url) + "\"").getMatch(0);
                if (id != null) {
                    logger.info("Found videoid");
                    parameter = "https://www.camwhores.tv/videos/" + id + "/" + filename_url;
                } else {
                    logger.info("Found no videoid at all");
                }
            }
            /* Probably a selfhosted video. */
            final DownloadLink dl = createDownloadlink(parameter);
            decryptedLinks.add(dl);
        }
        return decryptedLinks;
    }
}

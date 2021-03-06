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

import java.io.File;
import java.util.ArrayList;

import org.jdownloader.captcha.v2.challenge.clickcaptcha.ClickedPoint;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperCrawlerPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "goldesel.to" }, urls = { "https?://(www\\.)?goldesel\\.to/[a-z0-9]+(/[a-z0-9\\-]+)?/\\d+.{4,}" })
public class GldSlTo extends antiDDoSForDecrypt {
    public GldSlTo(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String HTML_CAPTCHA       = "Klicke in den gestrichelten Kreis, der sich somit von den anderen unterscheidet";
    private static final String HTML_LIMIT_REACHED = "class=\"captchaWait\"";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(true);
        getPage(parameter);
        if (this.br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String fpName = br.getRegex("<title>\\s*([^<>\"]*?)\\s*\\&raquo; goldesel\\.to\\s*</title>").getMatch(0);
        if (fpName == null) {
            fpName = br.getRegex("<title>\\s*([^<>\"]*?)\\s*</title>").getMatch(0);
        }
        if (fpName == null) {
            fpName = new Regex(br.getURL(), "goldesel\\.to/.*/(.+)").getMatch(0);
        }
        fpName = Encoding.htmlDecode(fpName).trim();
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);
        String[] decryptIDs = br.getRegex("data\\s*=\\s*\"([^<>\"]*?)\"").getColumn(0);
        if (decryptIDs == null || decryptIDs.length == 0) {
            /* 2020-10-19: Some URLs only got P2P/usenet sources available! */
            logger.info("Failed to find any OCH mirrors");
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        br.getHeaders().put("Accept", "*/*");
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        final int maxc = decryptIDs.length;
        int counter = 1;
        boolean captchafailed = false;
        for (final String decryptID : decryptIDs) {
            if (this.isAbort()) {
                logger.info("Decryption aborted by user: " + parameter);
                return decryptedLinks;
            }
            // br.setCookie("goldesel.to", "__utma", "222304525.384242273.1432990594.1432990594.1433159390.2");
            // br.setCookie("goldesel.to", "__utmz", "222304525.1432990594.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none)");
            // br.setCookie("goldesel.to", "__utmb", "222304525.1.10.1433159390");
            // br.setCookie("goldesel.to", "__utmc", "222304525");
            /* IMPORTANT */
            br.setCookie("goldesel.to", "__utmt", "1");
            postPage("https://goldesel.to/res/links", "data=" + Encoding.urlEncode(decryptID));
            if (br.containsHTML(HTML_CAPTCHA)) {
                for (int i = 1; i <= 3; i++) {
                    if (this.isAbort()) {
                        logger.info("Decryption aborted by user: " + parameter);
                        return decryptedLinks;
                    }
                    final String capLink = br.getRegex("\"(inc/cirlecaptcha\\.php[^<>\"]*?)\"").getMatch(0);
                    if (capLink == null) {
                        logger.warning("Decrypter broken for link: " + parameter);
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    final File file = this.getLocalCaptchaFile();
                    getCaptchaBrowser(br).getDownload(file, "https://goldesel.to/" + capLink);
                    String click_on;
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        click_on = "Klicke in den gestrichelten Kreis!";
                    } else {
                        click_on = "Click in the dashed circle!";
                    }
                    final ClickedPoint cp = getCaptchaClickedPoint(getHost(), file, param, "Goldesel.to\r\nDecrypting: " + fpName + "\r\nClick-Captcha | Mirror " + counter + " / " + maxc + " : " + decryptID, click_on);
                    if (cp == null) {
                        throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                    }
                    postPage("https://goldesel.to/res/links", "data=" + Encoding.urlEncode(decryptID) + "&xC=" + cp.getX() + "&yC=" + cp.getY());
                    if (br.containsHTML(HTML_LIMIT_REACHED)) {
                        logger.info("We have to wait because the user entered too many wrong captchas...");
                        int wait = 60;
                        String waittime = br.getRegex("<strong>(\\d+) Sekunden</strong> warten\\.").getMatch(0);
                        if (waittime != null) {
                            wait = Integer.parseInt(waittime);
                        } else {
                            logger.info("Did not find any short waittime --> Probably hourly limit is reached --> Stopping decryption");
                            break;
                        }
                        this.sleep(wait * 1001, param);
                        br.postPage("https://goldesel.to/res/links", "data=" + Encoding.urlEncode(decryptID));
                        continue;
                    }
                    if (br.containsHTML(HTML_CAPTCHA)) {
                        captchafailed = true;
                        continue;
                    }
                    captchafailed = false;
                    break;
                }
                if (captchafailed) {
                    logger.info("Captcha failed for decryptID: " + decryptID);
                    continue;
                }
            } else if (br.containsHTML("\"g\\-recaptcha\"")) {
                final String recaptchaV2Response = new CaptchaHelperCrawlerPluginRecaptchaV2(this, br).getToken();
                postPage("https://goldesel.to/res/links", "data=" + Encoding.urlEncode(decryptID) + "&rcc=" + Encoding.urlEncode(recaptchaV2Response));
            }
            if (br.containsHTML(HTML_LIMIT_REACHED)) {
                logger.info("Probably hourly limit is reached --> Stopping decryption");
                return decryptedLinks;
            }
            final String[] finallinks = br.getRegex("url\\s*=\\s*\"(https?[^<>\"]*?)\"").getColumn(0);
            for (final String finallink : finallinks) {
                final DownloadLink dl = createDownloadlink(Encoding.htmlDecode(finallink));
                fp.add(dl);
                distribute(dl);
                decryptedLinks.add(dl);
            }
            counter++;
        }
        /* Only 1 link + wrong captcha --> */
        if (decryptedLinks.size() == 0 && captchafailed) {
            throw new PluginException(LinkStatus.ERROR_CAPTCHA);
        }
        if (decryptedLinks.size() == 0) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        fp.addLinks(decryptedLinks);
        return decryptedLinks;
    }

    /* Prevent confusion */
    public int getMaxConcurrentProcessingInstances() {
        return 1;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}
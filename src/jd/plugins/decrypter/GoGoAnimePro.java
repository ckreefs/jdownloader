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

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.requests.GetRequest;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "gogoanime.pro" }, urls = { "https?://(www\\d*\\.)?gogoanime\\.pro/anime/[^/]+\\d+.+" })
public class GoGoAnimePro extends antiDDoSForDecrypt {
    public GoGoAnimePro(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(true);
        getPage(parameter);
        String fpName = br.getRegex("<title>(?:Gogoanime - Watch\\s*)([^<]+)\\s+in\\s+HD\\s+-\\s+GogoAnime").getMatch(0);
        String[] details = br.getRegex("<div[^>]+id\\s*=\\s*\"watch\"[^>]+data-id\\s*=\\s*\"([^\"]*)\"[^>]+data-ep-name-normalized\\s*=\\s*\"([^\"]*)\"[^>]*>").getRow(0);
        String titleID = details[0];
        String episodeID = details[1];
        final GetRequest getEpisodes = new GetRequest(br.getURL("/ajax/film/servers/" + titleID + "?ep=&episode=" + episodeID).toString());
        getEpisodes.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        String videoDetails = (String) JavaScriptEngineFactory.walkJson(JSonStorage.restoreFromString(br.getPage(getEpisodes), TypeRef.HASHMAP), "html");
        if (StringUtils.isNotEmpty(videoDetails)) {
            final GetRequest getKey = new GetRequest("https://mcloud.to/key");
            getKey.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            Browser br2 = br.cloneBrowser();
            String mcloud = new Regex(br2.getPage(getKey), "window\\.mcloudKey\\s*=\\s*['\"]\\s*([^'\"]+)\\s*['\"]").getMatch(0);
            String[][] episodeDetails = new Regex(videoDetails, "data-name\\s*=\\s*\"([^\"]*)\"[^>]+data-name-normalized\\s*=\\s*\"([^\"]*)\"[^>]+data-servers\\s*=\\s*\"([^\"]*)\"").getMatches();
            if (episodeDetails != null) {
                for (String[] episodeDetail : episodeDetails) {
                    String episodeName = episodeDetail[0];
                    String episodeTitle = episodeDetail[1];
                    String serverIDList = episodeDetail[2];
                    if (StringUtils.isNotEmpty(serverIDList)) {
                        for (String serverID : serverIDList.split(",")) {
                            final GetRequest getEpisode = new GetRequest(br.getURL("/ajax/episode/info?filmId=" + titleID + "&server=" + serverID + "&episode=" + Encoding.urlEncode(episodeName) + "&mcloud=" + mcloud).toString());
                            getEpisode.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                            String getEpisodeResponse = br.getPage(getEpisode);
                            if (br.containsHTML("503 Service Temporarily Unavailable")) {
                                Thread.sleep(1000);
                                getEpisodeResponse = br.getPage(getEpisode);
                            }
                            String target = (String) JavaScriptEngineFactory.walkJson(JSonStorage.restoreFromString(getEpisodeResponse, TypeRef.HASHMAP), "target");
                            if (StringUtils.isNotEmpty(target)) {
                                decryptedLinks.add(createDownloadlink(Encoding.htmlOnlyDecode(target)));
                            }
                            Thread.sleep(100);
                        }
                    }
                }
            }
        }
        //
        if (StringUtils.isNotEmpty(fpName)) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }
}
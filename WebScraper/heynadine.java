package WebScraper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class heynadine {
	private static HashMap<String, Integer> urlMap = new HashMap<>();
	private static String blogBaseDir = "/tmp/travelblog/";
	private static String metaBaseDir = "/tmp/travelblog-meta/";
	private static ScraperUtil su = ScraperUtil.getInstance();
	private static Configuration config = null;
	private static Logger logger = LoggerFactory.getLogger(travelblog.class);

	private static void parseHtml(String blogUrl, Path outputFile, FSDataOutputStream mapbw, FileSystem fs) {
		try {
			logger.info("Extracting: " + blogUrl);
			HttpClient client = HttpClientBuilder.create().build();
			HttpGet httpGet = new HttpGet(blogUrl);
	        HttpResponse response = client.execute(httpGet);
	        HttpEntity entity = response.getEntity();
	        String responseString = EntityUtils.toString(entity, "UTF-8");
	        Document doc = Jsoup.parse(responseString);
	        Elements list = doc.select("title");
	        Element titleElement = list.size() > 0 ? list.first() : null;
	        String title = list.size() > 0 ? titleElement.ownText():"Unknown";
	        Elements tags = doc.select("article[id]");
	        List<String> tagStr = new ArrayList<>();
	        if (tags.size() > 0) {
	        	String tagLine = tags.first().attr("class");
	        	String[] tagarr = tagLine.split(" ");
	        	for(int i = 0; i < tagarr.length; i++) {
	        		int idx = tagarr[i].indexOf("tag");
	        		if (idx == 0)
	        			tagStr.add(tagarr[i].substring(4, tagarr[i].length()));
	        	}
	        }
	        Elements divlist = doc.select("div[class=post-entry]");
	        String buf = "";
	        for(Element e:divlist) {
	        	String text = "";
                for (TextNode tn : e.textNodes()) {
                    String tagText = tn.text().trim();
                    if (tagText.length() > 0) {
                        text += tagText + " ";
                    }
                }
                buf = su.removeStopWords(text);
	        }
	        if (buf.length() > 50) {
	        	mapbw.writeUTF("{\"url\":\"" + blogUrl + "\",\"title\": \"" + title + "\",\"file\":\"" + outputFile + "\",\"continent\": \"\","
		        		+ "\"country\":\"\", \"state\":\"\",\"area\":\"\",\"topics\":\"" + String.join(",", tagStr) + "\"},\n");
	        	if (fs.exists(outputFile))
	        		fs.delete(outputFile);
	        	FSDataOutputStream fin = fs.create(outputFile);
	        	fin.writeUTF(buf.toString() + "\n");
	        	fin.close();
	        }
	        EntityUtils.consume(entity);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		su.initStopWords();
		config = su.setupHdfsConfig("/opt/hadoop-2.7.3");
		su.createHdfsDirectory(config, blogBaseDir);
		su.createHdfsDirectory(config, metaBaseDir);
		
		urlMap.put("http://www.heynadine.com/category/travel/oceania/", 4);
		urlMap.put("http://www.heynadine.com/category/travel/africa/", 1);
		urlMap.put("http://www.heynadine.com/category/travel/asia-travel/", 4);
		urlMap.put("http://www.heynadine.com/category/travel/asia-travel/southeast-asia-travel/", 3);
		urlMap.put("http://www.heynadine.com/category/travel/europe-travel-2/western-europe/", 1);
		urlMap.put("http://www.heynadine.com/category/travel/europe-travel-2/eastern-europe/", 2);
		urlMap.put("http://www.heynadine.com/category/travel/europe-travel-2/mediterranean/", 1);
		urlMap.put("http://www.heynadine.com/category/travel/europe-travel-2/scandinavia/", 1);
		urlMap.put("http://www.heynadine.com/category/travel/europe-travel-2/united-kingdom/", 1);
		urlMap.put("http://www.heynadine.com/category/travel/americas/north-america/", 3);
		urlMap.put("http://www.heynadine.com/category/travel/americas/central-america/", 1);
		urlMap.put("http://www.heynadine.com/category/travel/americas/south-america-travel/", 2);
		urlMap.put("http://www.heynadine.com/category/travel/caribbean/", 1);

		List<String> urlList = new ArrayList<>();

		try {
			FileSystem fs = FileSystem.get(config);

			for (String urlBase: urlMap.keySet()) {
				int totalPages = (int)urlMap.get(urlBase);
			
				for (int i = 1; i <= totalPages; i ++ ) {
					String url = urlBase;
					if ( i > 1)
						url += "page/" + i + "/";
					//System.out.println(url);
					HttpClient client = HttpClientBuilder.create().build();
					HttpGet httpGet = new HttpGet(url);
			        HttpResponse response = client.execute(httpGet);
			        HttpEntity entity = response.getEntity();
			        String responseString = EntityUtils.toString(entity, "UTF-8");
					Document doc = Jsoup.parse(responseString);
					Elements links = doc.select("div[class=post-header] > h2"); // a with title
					links.forEach(e -> {
						String href = e.child(0).attr("href");
						urlList.add(href);
						//System.out.println(href);
					});
					EntityUtils.consume(entity);
				}
			}
			Path file = new Path(metaBaseDir + "heynadine-mapping.json");
			if (fs.exists(file))
				fs.delete(file);
			FSDataOutputStream bw = fs.create(file);
			int cnt = 0;
			for(String blogUrl : urlList) {
				cnt ++ ;
				Path blogOutput = new Path(blogBaseDir + "heynadine/blog_" + cnt + ".txt");
				parseHtml(blogUrl, blogOutput, bw, fs);
			};
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
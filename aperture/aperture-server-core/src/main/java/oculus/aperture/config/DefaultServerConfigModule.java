/**
 * Copyright (c) 2013 Oculus Info Inc. 
 * http://www.oculusinfo.com/
 * 
 * Released under the MIT License.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package oculus.aperture.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties;

import javax.servlet.ServletContext;

import oculus.aperture.common.UtilProperties;
import oculus.aperture.common.util.ResourceHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class DefaultServerConfigModule extends AbstractModule {

	final Logger logger = LoggerFactory.getLogger(getClass());

	private final ServletContext context;

	public DefaultServerConfigModule(ServletContext context) {
		this.context = context;
	}


	@Override
	protected void configure() {

		InputStream inp = null;
		
		try {
			/*
			 * Load default property values
			 */
			inp = ResourceHelper.getStreamForPath("res:///default.properties", null);

			Properties properties = new Properties();
			properties.load(inp);

			Closeables.closeQuietly(inp);

			
			String build = properties.getProperty("aperture.buildnumber");
			
			// Output build number
			if (build != null) {
				logger.info("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
				logger.info("Aperture version: " + build);
				logger.info("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
			}

			/*
			 * Override Properties
			 */
			logger.info("Loading app properties...");

			// Read the charitynetConfig property from web.xml or override-web.xml
			String filename = context.getInitParameter("apertureConfig");

			// Load properties
			inp = ResourceHelper.getStreamForPath(filename, "res:///aperture-app.properties");

			// Load overrides
			if (inp != null) {
				
				properties = new Properties(properties);
				properties.load(inp);
	
				build = properties.getProperty("app.buildnumber");
				
				// Output build number
				if (build != null) {
					logger.info("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
					logger.info("Application version: " + build);
					logger.info("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
				}
				
				Closeables.closeQuietly(inp);
				inp = null;
			}

			// trace
			if (logger.isDebugEnabled()) {
				properties.list(System.out);
			}
			
			// wrap with aperture spi
			UtilProperties aprops = new UtilProperties(properties);
			
			// bind the set of properties in guice
			bind(oculus.aperture.spi.common.Properties.class).annotatedWith(
					Names.named("aperture.server.config")).toInstance(aprops);

			
			// Optionally bind all properties to named annotations (also in guice)
			if (aprops.getBoolean("aperture.server.config.bindnames", false)) {
				Names.bindProperties(this.binder(), properties );
			}

			// Output all properties values to log
			if( logger.isDebugEnabled() ) {
				for( Object key : properties.keySet() ) {
					logger.debug("System Setting - " + key + ": " + properties.get(key));
				}
			}

			logger.info("Checking for REST supplied aperture.client.configfile...");
			
			// If client config is to be served by REST (typically used to grab it from an editable file)...
			inp = ResourceHelper.getStreamForPath(aprops.getString("aperture.client.configfile", null), null);

			String json= null;
			
			if (inp != null) {
				try {
					json = CharStreams.toString(new BufferedReader(
							new InputStreamReader(inp, Charset.forName("UTF-8"))));
				} finally {
					Closeables.closeQuietly(inp);
				}
			}
			
			// bind result in guice.
			Names.bindProperties(this.binder(), ImmutableMap.of("aperture.client.config", json != null? json: "{}"));

		} catch (IOException e) {
			// Failed to load properties, error
			addError(e);
			
			if (inp != null) {
				Closeables.closeQuietly(inp);
			}
		}

	}

}

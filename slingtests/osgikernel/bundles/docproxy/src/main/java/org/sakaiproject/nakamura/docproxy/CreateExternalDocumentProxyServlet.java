/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.docproxy;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.docproxy.DocProxyConstants;
import org.sakaiproject.nakamura.api.docproxy.DocProxyException;
import org.sakaiproject.nakamura.api.docproxy.ExternalRepositoryProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

/**
 * Creates a external document resource where there was none, conforming to the standard
 * Sling protocol.
 */
@SlingServlet(resourceTypes = { "sakai/external-repository" }, selectors = { "create" }, methods = { "POST" }, generateComponent = true, generateService = true)
public class CreateExternalDocumentProxyServlet extends SlingAllMethodsServlet {

  protected static final Logger LOGGER = LoggerFactory
      .getLogger(CreateExternalDocumentProxyServlet.class);
  protected ExternalRepositoryProcessorTracker tracker;
  private static final long serialVersionUID = -3606817798030170480L;
  protected static final String PARAM_FILENAME = "filename";
  protected static final String PARAM_FILEBODY = "filebody";

  /**
   * {@inheritDoc}
   * 
   * @see org.apache.sling.api.servlets.SlingAllMethodsServlet#doPost(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    try {
      // Anonymous users can't do anything.
      if (request.getRemoteUser().equals("anon")) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
            "Anonymous users can't post anything.");
        return;
      }

      // Check required parameters.
      RequestParameter filename = request.getRequestParameter(PARAM_FILENAME);
      RequestParameter filebody = request.getRequestParameter(PARAM_FILEBODY);

      if (filename == null || filebody == null || filebody.isFormField()) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "Not all required parameters were supplied.");
        return;
      }

      // Get proxy node and the type of processor to use
      Node node = request.getResource().adaptTo(Node.class);
      String processorType = node.getProperty(DocProxyConstants.REPOSITORY_PROCESSOR)
          .getString();
      ExternalRepositoryProcessor processor = tracker.getProcessorByType(processorType);
      if (processor == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown repository.");
        return;
      }

      // Upload the file.
      InputStream stream = filebody.getInputStream();

      // Get index of this

      String path;
      if (filename.getString().equals("")) {
        path = filebody.getFileName();
      } else {
        path = filename.getString() + "/" + filebody.getFileName();
      }
      processor.updateDocument(node, path, null, stream, filebody.getSize());

    } catch (RepositoryException e) {
      LOGGER.error("Failed to proxy document", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Failed to get node properties.");
    } catch (DocProxyException e) {
      LOGGER.error("Failed to proxy document", e);
      response.sendError(e.getCode(), e.getMessage());
    }
  }

  protected void activate(ComponentContext context) {
    BundleContext bundleContext = context.getBundleContext();
    tracker = new ExternalRepositoryProcessorTracker(bundleContext,
        ExternalRepositoryProcessor.class.getName(), null);
    tracker.open();
  }

  protected void deactivate(ComponentContext context) {
    if (tracker != null) {
      tracker.close();
      tracker = null;
    }
  }
}

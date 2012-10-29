/*
 * Copyright (c) 2002-2012, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.extend.service.content;

import fr.paris.lutece.plugins.extend.business.extender.ResourceExtenderDTO;
import fr.paris.lutece.plugins.extend.service.converter.IStringMapper;
import fr.paris.lutece.plugins.extend.service.extender.IResourceExtenderService;
import fr.paris.lutece.portal.business.page.Page;
import fr.paris.lutece.portal.service.content.ContentPostProcessor;
import fr.paris.lutece.portal.service.template.AppTemplateService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.html.HtmlTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;


/**
 *
 * This content post processor replace all macro of type @Extender[idResource,resourceType,extenderType,parameters]@
 * to the associated extender content.
 *
 */
public class ExtendableContentPostProcessor implements ContentPostProcessor, InitializingBean
{
    private static final String NAME = "Extend content processor";
    private static final String END_BODY = "</body>";

    // PROPERTIES
    private static final String PROPERTY_CLIENT_SIDE = "extend.contentPostProcessor.clientSide";

    // MARKS
    private static final String MARK_REGEX_PATTERN = "extendRegexPattern";
    private static final String MARK_BASE_URL = "baseUrl";

	private static final String PARAM_PAGE = "page";

    // TEMPLATES
    private static final String TEMPLATE_CONTENT_POST_PROCESSOR = "skin/plugins/extend/extendable_content_post_processor.html";
    @Inject
    private IResourceExtenderService _extenderService;
    @Inject
    private IStringMapper<ResourceExtenderDTO> _mapper;
    private String _strRegexPattern;

    /**
     * Sets the regex pattern.
     *
     * @param strRegexPattern the new regex pattern
     */
    public void setRegexPattern( String strRegexPattern )
    {
        _strRegexPattern = strRegexPattern;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName(  )
    {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String process( HttpServletRequest request, String strContent )
    {
        String strHtmlContent = strContent;

        // Check if the process is carried out in client or server side
        boolean bClientSide = Boolean.valueOf( AppPropertiesService.getProperty( PROPERTY_CLIENT_SIDE, "false" ) );

        if ( bClientSide )
        {
            // CLIENT SIDE
            int nPos = strHtmlContent.indexOf( END_BODY );

            if ( nPos < 0 )
            {
                AppLogService.error( "ExtendableContentPostProcessor Service : no BODY end tag found" );

                return strHtmlContent;
            }

            Map<String, Object> model = new HashMap<String, Object>(  );
            model.put( MARK_BASE_URL, AppPathService.getBaseUrl( request ) );
            model.put( MARK_REGEX_PATTERN, _strRegexPattern );

            HtmlTemplate template = AppTemplateService.getTemplate( TEMPLATE_CONTENT_POST_PROCESSOR,
                    request.getLocale(  ), model );

            StringBuilder sb = new StringBuilder(  );
            sb.append( strHtmlContent.substring( 0, nPos ) );
            sb.append( template.getHtml(  ) );
            sb.append( strHtmlContent.substring( nPos ) );
            strHtmlContent = sb.toString(  );
        }
        else
        {
            // SERVER SIDE

            /**
             * Replace all makers @Extender[<idResource>,<resourceType>,<extenderType>,<params>]@ to
             * the correct HTML content of the extender.
             * 1) First parse the content of the markers
             * 2) Get all information (idResource, resourceType, extenderType, params)
             * 3) Get the html content from the given information
             * 4) Replace the markers by the html content
             */

            // 1) First parse the content of the markers
            Pattern pattern = Pattern.compile( _strRegexPattern );
            Matcher match = pattern.matcher( strHtmlContent );

            while ( match.find(  ) )
            {
                String strMarker = match.group(  );

                // 2) Get all information (idResource, resourceType, extenderType, params)
                ResourceExtenderDTO resourceExtender = _mapper.map( match.group( 1 ) );

                // 3) Get the html content from the given information
				String strHtml = StringUtils.EMPTY;
				if ( !StringUtils.equals( resourceExtender.getExtendableResourceType( ), Page.RESOURCE_TYPE ) || StringUtils.isBlank( request.getParameter( PARAM_PAGE ) ) )
				{
					strHtml = _extenderService.getContent( resourceExtender.getIdExtendableResource( ),
                        resourceExtender.getExtendableResourceType(  ), resourceExtender.getExtenderType(  ),
                        resourceExtender.getParameters(  ), request );
				}

                // 4) Replace the markers by the html content
                strHtmlContent = strHtmlContent.replaceAll( Pattern.quote( strMarker ), strHtml );
            }
        }

        return strHtmlContent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet(  ) throws Exception
    {
        Assert.notNull( _strRegexPattern, "The property 'regexPattern' is required." );
    }
}

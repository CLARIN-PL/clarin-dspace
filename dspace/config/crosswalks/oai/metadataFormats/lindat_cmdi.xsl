<?xml version="1.0" encoding="UTF-8" ?>
<!-- 
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:doc="http://www.lyncode.com/xoai"
    xmlns:fn="http://custom.crosswalk.functions"
	xmlns:fnx="http://www.w3.org/2005/xpath-functions"
    xmlns:xalan="http://xml.apache.org/xslt"
    xmlns:ms="http://www.ilsp.gr/META-XMLSchema"
    xmlns:olac="http://experimental.loc/olac"
    xmlns:cmd="http://www.clarin.eu/cmd/"
    xmlns:lindat="http://lindat.mff.cuni.cz/ns/experimental/cmdi"
    exclude-result-prefixes="doc xalan fn fnx ms" version="1.0">
    <xsl:import href="metasharev2.xsl"/>
    <xsl:import href="olac-dcmiterms.xsl"/>
    
    <xsl:output omit-xml-declaration="yes" method="xml" indent="yes" xalan:indent-amount="4"/>
    <xsl:namespace-alias stylesheet-prefix="ms" result-prefix="cmd"/>
    <!-- #default probably not working -->
    <xsl:namespace-alias stylesheet-prefix="olac" result-prefix="cmd"/>


    <xsl:variable name="handle" select="/doc:metadata/doc:element[@name='others']/doc:field[@name='handle']/text()"/>
    <xsl:variable name="dc_identifier_uri"
    select="fn:stringReplace(/doc:metadata/doc:element[@name='dc']/doc:element[@name='identifier']/doc:element[@name='uri']/doc:element/doc:field[@name='value'])"/>
    <xsl:variable name="modifyDate" select="/doc:metadata/doc:element[@name='others']/doc:field[@name='lastModifyDate']/text()"/>
    <xsl:variable name="accessionDate"
        select="/doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='accessioned']/doc:element/doc:field[@name='value'][1]"/>
    <xsl:variable name="availableDate"
        select="/doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='available']/doc:element/doc:field[@name='value'][1]"/>
    <xsl:variable name="issuedDate"
        select="/doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value'][1]"/>
    <xsl:variable name="wordCount"
        select="/doc:metadata/doc:element[@name='local']/doc:element[@name='size']/doc:element[@name='info']/doc:element/doc:field[@name='value'][contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), ';words')][1]"/>
    <xsl:variable name="dc_rights_uri" select="/doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element[@name='uri']/doc:element/doc:field[@name='value']" />
    <xsl:variable name="serverURL" select="fn:getProperty('dspace.server.url')"/>
    <xsl:variable name="cmdiSelfLink" select="concat($serverURL, '/cmdi/oai-metadata?metadataPrefix=cmdi&amp;handle=', $handle)"/>
    <xsl:variable name="newProfile" select="'clarin.eu:cr1:p_1403526079380'"/>
    <xsl:variable name="oldProfile" select="'clarin.eu:cr1:p_1349361150622'"/>
    
    <xsl:template match="/">
        <xsl:variable name="uploaded_md" select="fn:getUploadedMetadata($handle)"/>
        <xsl:choose>
            <xsl:when test="$uploaded_md != ''">
                <!--
                    Historical records may contain an xs:dateTime (or a textual Java date)
                    in MdCreationDate and stale MdSelfLink values. Normalise their CMDI
                    envelope at dissemination time without modifying the archived bitstream.
                -->
                <xsl:apply-templates select="$uploaded_md/*" mode="normalise-uploaded-cmdi"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:call-template name="ConstructCMDI"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="@*|node()" mode="normalise-uploaded-cmdi">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="normalise-uploaded-cmdi"/>
        </xsl:copy>
    </xsl:template>

    <!-- Return an xs:date value without assuming a database-specific Date#toString representation. -->
    <xsl:template name="CmdiCreationDate">
        <xsl:choose>
            <xsl:when test="substring(normalize-space($modifyDate), 5, 1) = '-' and substring(normalize-space($modifyDate), 8, 1) = '-'">
                <xsl:value-of select="substring(normalize-space($modifyDate), 1, 10)"/>
            </xsl:when>
            <xsl:when test="substring(normalize-space($accessionDate), 5, 1) = '-' and substring(normalize-space($accessionDate), 8, 1) = '-'">
                <xsl:value-of select="substring(normalize-space($accessionDate), 1, 10)"/>
            </xsl:when>
            <xsl:when test="substring(normalize-space($availableDate), 5, 1) = '-' and substring(normalize-space($availableDate), 8, 1) = '-'">
                <xsl:value-of select="substring(normalize-space($availableDate), 1, 10)"/>
            </xsl:when>
            <xsl:when test="substring(normalize-space($issuedDate), 5, 1) = '-' and substring(normalize-space($issuedDate), 8, 1) = '-'">
                <xsl:value-of select="substring(normalize-space($issuedDate), 1, 10)"/>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

    <!-- Keep the schema-defined Header order and add the optional fields when old CMDI omitted them. -->
    <xsl:template match="*[local-name()='Header']" mode="normalise-uploaded-cmdi">
        <xsl:variable name="creationDate">
            <xsl:choose>
                <xsl:when test="*[local-name()='MdCreationDate'][substring(normalize-space(.), 5, 1) = '-' and substring(normalize-space(.), 8, 1) = '-']">
                    <xsl:value-of select="substring(normalize-space(*[local-name()='MdCreationDate'][1]), 1, 10)"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="CmdiCreationDate"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:copy>
            <xsl:apply-templates select="@*" mode="normalise-uploaded-cmdi"/>
            <xsl:apply-templates select="*[local-name()='MdCreator']" mode="normalise-uploaded-cmdi"/>
            <xsl:if test="string-length(normalize-space($creationDate)) = 10">
                <cmd:MdCreationDate><xsl:value-of select="$creationDate"/></cmd:MdCreationDate>
            </xsl:if>
            <cmd:MdSelfLink><xsl:value-of select="$cmdiSelfLink"/></cmd:MdSelfLink>
            <xsl:apply-templates
                select="*[not(local-name()='MdCreator' or local-name()='MdCreationDate' or local-name()='MdSelfLink')]"
                mode="normalise-uploaded-cmdi"/>
        </xsl:copy>
    </xsl:template>

    <!--
        Repair empty legacy word counts only when an audited value exists in
        local.size.info (for example "62592;words"). Otherwise omit the invalid
        empty decimal instead of inventing a value.
    -->
    <xsl:template match="*[local-name()='NumberOfWords'][not(normalize-space())]"
                  mode="normalise-uploaded-cmdi">
        <xsl:if test="$wordCount">
            <xsl:copy>
                <xsl:apply-templates select="@*" mode="normalise-uploaded-cmdi"/>
                <xsl:value-of select="substring-before(concat(normalize-space($wordCount), ';'), ';')"/>
            </xsl:copy>
        </xsl:if>
    </xsl:template>
    
    <xsl:template name="ConstructCMDI">
    	<xsl:variable name="contact" select="/doc:metadata/doc:element[@name='local']/doc:element[@name='contact']/doc:element[@name='person']/doc:element/doc:field[@name='value']"/>
    	<xsl:variable name="profile">
                    <xsl:choose>
                            <xsl:when test="$contact != '' and $dc_rights_uri != ''">
                            	<xsl:value-of select="$newProfile"/>
                            </xsl:when>
                            <xsl:otherwise>
                            	<xsl:value-of select="$oldProfile"/>
                            </xsl:otherwise>
                    </xsl:choose>
                    
        </xsl:variable>
        <cmd:CMD CMDVersion="1.1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        	<xsl:attribute name="xsi:schemaLocation">
                <xsl:value-of select="concat('http://www.clarin.eu/cmd/ https://catalog.clarin.eu/ds/ComponentRegistry/rest/registry/1.1/profiles/',$profile,'/xsd')"/>
        	</xsl:attribute>
            <xsl:call-template name="AdministrativeMD">
            	<xsl:with-param name="profile" select="$profile"/>
            </xsl:call-template>
            <xsl:choose>
            	<xsl:when test="$profile = $newProfile">
                    <xsl:call-template name="NewComponents"/>
            	</xsl:when>
            	<xsl:otherwise>
            		<xsl:call-template name="OldComponents"/>
            	</xsl:otherwise>
            </xsl:choose>
        </cmd:CMD>
    </xsl:template>
    
    <xsl:template name="AdministrativeMD">
    	<xsl:param name="profile"/>
        <xsl:call-template name="Header">
        	<xsl:with-param name="profile" select="$profile"/>
        </xsl:call-template>
        <xsl:call-template name="Resources"/>
    </xsl:template>
    
    <xsl:template name="Header">
    	<xsl:param name="profile"/>
        <xsl:variable name="creationDate"><xsl:call-template name="CmdiCreationDate"/></xsl:variable>
        <cmd:Header>
            <xsl:if test="string-length(normalize-space($creationDate)) = 10">
                <cmd:MdCreationDate><xsl:value-of select="$creationDate"/></cmd:MdCreationDate>
            </xsl:if>
            <cmd:MdSelfLink><xsl:value-of select="$cmdiSelfLink"/></cmd:MdSelfLink>
            <cmd:MdProfile><xsl:value-of select="$profile"/></cmd:MdProfile>
            <cmd:MdCollectionDisplayName><xsl:value-of select="/doc:metadata/doc:element[@name='others']/doc:field[@name='owningCollection']/text()"/></cmd:MdCollectionDisplayName>
        </cmd:Header>
    </xsl:template>

	<xsl:template name="Resources">
		<cmd:Resources>
			<cmd:ResourceProxyList>
				<cmd:ResourceProxy>
					<xsl:attribute name="id">lp_<xsl:value-of select="/doc:metadata/doc:element[@name='others']/doc:field[@name='itemId']/text()" /></xsl:attribute>
					<cmd:ResourceType>LandingPage</cmd:ResourceType>
					<cmd:ResourceRef>
						<xsl:value-of select="$dc_identifier_uri" />
					</cmd:ResourceRef>
				</cmd:ResourceProxy>
				<xsl:call-template name="ProcessSourceURI"/>
				<xsl:call-template name="ProcessBitstreams"/>
			</cmd:ResourceProxyList>
			<cmd:JournalFileProxyList/>
			<cmd:ResourceRelationList/>
		</cmd:Resources>
	</xsl:template>
	
	<!-- List only the data files (ORIGINAL bundle); the download endpoint below serves nothing else -->
	<xsl:template name="ProcessBitstreams">
	   <xsl:for-each select="/doc:metadata/doc:element[@name='bundles']/doc:element[@name='bundle']/doc:field[@name='name' and text()='ORIGINAL']/../doc:element[@name='bitstreams']/doc:element[@name='bitstream']">
	       <cmd:ResourceProxy>
	                   <xsl:attribute name="id">_<xsl:value-of select="./doc:field[@name='id']/text()"/></xsl:attribute>
                       <cmd:ResourceType><xsl:attribute name="mimetype"><xsl:value-of select="./doc:field[@name='format']/text()"/></xsl:attribute>Resource</cmd:ResourceType>
			   <cmd:ResourceRef><xsl:attribute name="lindat:md5_checksum"><xsl:value-of select="./doc:field[@name='checksum']/text()"/></xsl:attribute><xsl:value-of select="concat($serverURL,'/api/core/bitstreams/handle/',$handle,'/',fnx:encode-for-uri(./doc:field[@name='name']/text()))"/></cmd:ResourceRef>
           </cmd:ResourceProxy>
	   </xsl:for-each>
	</xsl:template>
	
	<xsl:template name="ProcessSourceURI">
	   <xsl:for-each select="fnx:distinct-values(doc:metadata/doc:element[@name='dc']/doc:element[@name='source']/doc:element[@name='uri']/doc:element/doc:field[@name='value'])">
	       <cmd:ResourceProxy>
	           <xsl:attribute name="id">uri_<xsl:value-of select="position()"/></xsl:attribute>
	           <cmd:ResourceType><xsl:attribute name="mimetype">text/html</xsl:attribute>Resource</cmd:ResourceType>
	           <cmd:ResourceRef><xsl:value-of select="."/></cmd:ResourceRef>
	       </cmd:ResourceProxy>
	   </xsl:for-each>
	</xsl:template>
	
	<xsl:template name="OldComponents">
		<cmd:Components>
			<cmd:data>
				<xsl:call-template name="OLAC_DCMI"/>
				<xsl:if test="doc:metadata/doc:element[@name='metashare']/doc:element[@name='ResourceInfo#IdentificationInfo']/doc:element[@name='resourceName']/doc:element/doc:field[@name='value']">
                    <xsl:call-template name="ResourceInfo">
                        <xsl:with-param name="ns" select='"http://www.clarin.eu/cmd/"'/>
                    </xsl:call-template>
				</xsl:if>
			</cmd:data>
		</cmd:Components>
	</xsl:template>
	
	<xsl:template name="NewComponents">
		<cmd:Components>
			<cmd:LINDAT_CLARIN>
				<xsl:call-template name="bibliography"/>
				<xsl:call-template name="dataInfo"/>
				<xsl:call-template name="licenseInfo"/>
				<!-- relationsInfo -->
			</cmd:LINDAT_CLARIN>
		</cmd:Components>
	</xsl:template>
	
	<xsl:template name="bibliography">
		<cmd:bibliographicInfo>
			<xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='source']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
				<cmd:projectUrl><xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='source']/doc:element[@name='uri']/doc:element/doc:field[@name='value']"/></cmd:projectUrl>
			</xsl:if>
			<cmd:titles>
				<cmd:title>
					<xsl:attribute name="xml:lang">en</xsl:attribute>
					<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='title']/doc:element/doc:field[@name='value']"/>
				</cmd:title>
			</cmd:titles>
			<cmd:authors>
				<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='contributor']/doc:element[@name='author']/doc:element/doc:field[@name='value']">
					<xsl:copy-of select="fn:getAuthor(.)"/>
				</xsl:for-each>
			</cmd:authors>
			<cmd:dates>
				<cmd:dateIssued>
					<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='date']/doc:element[@name='issued']/doc:element/doc:field[@name='value']"/>
                </cmd:dateIssued>                
			</cmd:dates>
			<cmd:identifiers>
				<cmd:identifier type="Handle"><xsl:value-of select="$dc_identifier_uri"/></cmd:identifier>
			</cmd:identifiers>
			<xsl:if test="doc:metadata/doc:element[@name='local']/doc:element[@name='sponsor']/doc:element/doc:field[@name='value']">
                <cmd:funds>
                    <xsl:for-each select="doc:metadata/doc:element[@name='local']/doc:element[@name='sponsor']/doc:element/doc:field[@name='value']">
                        <xsl:copy-of select="fn:getFunding(.)"/>
                    </xsl:for-each>
                </cmd:funds>
            </xsl:if>
			<xsl:copy-of select="fn:getContact(doc:metadata/doc:element[@name='local']/doc:element[@name='contact']/doc:element[@name='person']/doc:element/doc:field[@name='value'])"/>
			<cmd:publishers>
				<cmd:publisher>
					<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='publisher']/doc:element/doc:field[@name='value']"/>
				</cmd:publisher>
			</cmd:publishers>
			
		</cmd:bibliographicInfo>
	</xsl:template>

	<xsl:template name="dataInfo">
		<cmd:dataInfo>
			<cmd:type>
					<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='type']/doc:element/doc:field[@name='value']"/>
			</cmd:type>
			<xsl:if test="doc:metadata/doc:element[@name='metashare']/doc:element[@name='ResourceInfo#ContentInfo']/doc:element[@name='detailedType']/doc:element/doc:field[@name='value']">
				<cmd:detailedType>
					<xsl:value-of select="doc:metadata/doc:element[@name='metashare']/doc:element[@name='ResourceInfo#ContentInfo']/doc:element[@name='detailedType']/doc:element/doc:field[@name='value']"/>
                </cmd:detailedType>
			</xsl:if>
			<cmd:description>
					<xsl:value-of select="doc:metadata/doc:element[@name='dc']/doc:element[@name='description']/doc:element/doc:field[@name='value']"/>
			</cmd:description>
			<xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element[@name='iso']/doc:element/doc:field[@name='value']">
				<cmd:languages>
					<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='language']/doc:element[@name='iso']/doc:element/doc:field[@name='value']">
						<cmd:language>
							<cmd:code><xsl:value-of select="."/></cmd:code>
							<cmd:name><xsl:value-of select="fn:getLangForCode(.)"/></cmd:name>
						</cmd:language>
                	</xsl:for-each>
				</cmd:languages>
			</xsl:if>
			<xsl:if test="doc:metadata/doc:element[@name='dc']/doc:element[@name='subject']/doc:element/doc:field[@name='value']">
				<cmd:keywords>
					<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='subject']/doc:element/doc:field[@name='value']">
						<cmd:keyword>
							<xsl:value-of select="."/>
						</cmd:keyword>
					</xsl:for-each>
				</cmd:keywords>
			</xsl:if>
			<xsl:if test="doc:metadata/doc:element[@name='local']/doc:element[@name='demo']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
				<cmd:links>
					<cmd:link>
						<xsl:value-of select="doc:metadata/doc:element[@name='local']/doc:element[@name='demo']/doc:element[@name='uri']/doc:element/doc:field[@name='value']"/>
					</cmd:link>
				</cmd:links>
			</xsl:if>
			<xsl:if test="doc:metadata/doc:element[@name='local']/doc:element[@name='size']/doc:element[@name='info']/doc:element/doc:field[@name='value']">
				<cmd:sizeInfo>
					<xsl:for-each select="doc:metadata/doc:element[@name='local']/doc:element[@name='size']/doc:element[@name='info']/doc:element/doc:field[@name='value']">
                          <xsl:copy-of select="fn:getSize(.)"/>
                	</xsl:for-each>
				</cmd:sizeInfo>
			</xsl:if>
		</cmd:dataInfo>
	</xsl:template>

	<xsl:template name="licenseInfo">
		<cmd:licenseInfo>
			<xsl:for-each select="doc:metadata/doc:element[@name='dc']/doc:element[@name='rights']/doc:element[@name='uri']/doc:element/doc:field[@name='value']">
				<cmd:license>
					<cmd:uri><xsl:value-of select="."/></cmd:uri>
				</cmd:license>
			</xsl:for-each>
		</cmd:licenseInfo>
	</xsl:template>
	
</xsl:stylesheet>

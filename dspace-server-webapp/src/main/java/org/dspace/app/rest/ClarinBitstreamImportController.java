/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.app.rest.utils.ContextUtil.obtainContext;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.converter.MetadataConverter;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.checker.service.MostRecentChecksumService;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.clarin.ClarinBitstreamService;
import org.dspace.content.service.clarin.ClarinItemService;
import org.dspace.services.ConfigurationService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Specialized controller created for Clarin-Dspace import bitstream.
 *
 * @author Michaela Paurikova (michaela.paurikova at dataquest.sk)
 */
@RestController
@RequestMapping("/api/clarin/import/" + BitstreamRest.CATEGORY)
public class ClarinBitstreamImportController {
    private static final Logger log = org.apache.logging.log4j.LogManager
            .getLogger(ClarinBitstreamImportController.class);
    @Autowired
    private BundleService bundleService;
    @Autowired
    private ClarinBitstreamService clarinBitstreamService;
    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private AuthorizeService authorizeService;
    @Autowired
    private MetadataConverter metadataConverter;
    @Autowired
    private ItemService itemService;
    @Autowired
    private BitstreamFormatService bitstreamFormatService;
    @Autowired
    private ConverterService converter;
    @Autowired
    private Utils utils;
    @Autowired
    private MostRecentChecksumService checksumService;
    @Autowired
    protected ClarinItemService clarinItemService;
    @Autowired
    private ConfigurationService configurationService;

    /**
     * Endpoint for import bitstream, whose file already exists in assetstore under internal_id
     * from request param.
     * The mapping for requested endpoint, for example
     * <pre>
     * {@code
     * https://<dspace.server.url>/api/clarin/import/core/bitstream
     * }
     * </pre>
     * @param request request
     * @return created bitstream converted to rest object
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method =  RequestMethod.POST, value = "/bitstream")
    public BitstreamRest importBitstreamForExistingFile(HttpServletRequest request) {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null!");
        }
        boolean validationDeferred = configurationService.getBooleanProperty(
                "clarin.bitstream.validation.deferred", false);
        boolean itemFilesMetadataDeferred = configurationService.getBooleanProperty(
                "clarin.item-files-metadata.deferred", false);
        // This fast path is available only while both explicit migration guards
        // are enabled. Normal repository writes continue through BundleService.
        boolean directBundleImport = validationDeferred && itemFilesMetadataDeferred;
        Bundle bundle = null;
        UUID bundleUUID = null;
        //bundle2bitstream
        String bundleUUIDString = request.getParameter("bundle_id");
        if (StringUtils.isNotBlank(bundleUUIDString)) {
            bundleUUID = UUID.fromString(bundleUUIDString);
            if (!directBundleImport) {
                try {
                    bundle = bundleService.find(context, bundleUUID);
                } catch (SQLException e) {
                    log.error("Something went wrong trying to find the Bundle with uuid: " + bundleUUID, e);
                }
            }
        }
        BitstreamRest bitstreamRest;
        Bitstream bitstream;
        Item item = null;
        try {
            //process bitstream creation
            ObjectMapper mapper = new ObjectMapper();
            bitstreamRest = mapper.readValue(request.getInputStream(), BitstreamRest.class);
            //create empty bitstream
            bitstream = clarinBitstreamService.create(context, directBundleImport ? null : bundle);
            //internal_id contains path to file
            String internalId = request.getParameter("internal_id");
            log.info("Going to process Bitstream with internal_id: " + internalId);
            bitstream.setInternalId(internalId);
            String storeNumberString = request.getParameter("storeNumber");
            bitstream.setStoreNumber(getIntegerFromString(storeNumberString));
            String sequenceIdString = request.getParameter("sequenceId");
            // Update sequenceId only if it is not `null`, sequenceId is set up to -1 by default.
            if (StringUtils.isNotBlank(sequenceIdString)) {
                Integer sequenceId = getIntegerFromString(sequenceIdString);
                bitstream.setSequenceID(sequenceId);
            } else {
                log.info("SequenceId is null. Bitstream UUID: " + bitstream.getID());
            }
            //add bitstream format
            String bitstreamFormatMimeType = request.getParameter("bitstreamFormat");
            BitstreamFormat bitstreamFormat = null;
            if (StringUtils.isNotBlank(bitstreamFormatMimeType)) {
                bitstreamFormat = bitstreamFormatService.findByMIMEType(context, bitstreamFormatMimeType);
            }
            bitstream.setFormat(context, bitstreamFormat);
            String deletedString = request.getParameter("deleted");

            Boolean deleted = Boolean.parseBoolean(deletedString);
            //set size bytes
            if (Objects.nonNull(bitstreamRest.getSizeBytes())) {
                bitstream.setSizeBytes(bitstreamRest.getSizeBytes());
            } else {
                log.info("SizeBytes is null. Bitstream UUID: " + bitstream.getID());
            }
            //set checksum
            bitstream.setChecksum(bitstreamRest.getCheckSum().getValue());
            //set checksum algorithm
            bitstream.setChecksumAlgorithm(bitstreamRest.getCheckSum().getCheckSumAlgorithm());
            //do validation between input fields and calculated fields based on file from assetstore

            // Validate by default. During an in-place migration the checksum scan may be
            // deferred until all legacy rows have been registered, so random access to a
            // large external assetstore does not serialize the database migration.
            if (deleted) {
                log.info("Validation is not checked for deleted bitstream id: " + bitstream.getID() +
                        ", because it may not exist in assetstore.");
            } else if (!validationDeferred) {
                if (!clarinBitstreamService.validation(context, bitstream)) {
                    log.info("Validation failed - return null. Bitstream UUID: " + bitstream.getID());
                    return null;
                }
            } else {
                log.debug("Assetstore validation deferred for imported bitstream: " + bitstream.getID());
            }
            if (bitstreamRest.getMetadata().getMap().size() > 0) {
                metadataConverter.setMetadata(context, bitstream, bitstreamRest.getMetadata());
            }

            // set bitstream as primary bitstream for bundle
            // if bitstream is not primary bitstream, bundle is null
            String primaryBundleUUIDString = request.getParameter("primaryBundle_id");
            if (!directBundleImport && StringUtils.isNotBlank(primaryBundleUUIDString)) {
                log.info("Bitstream has primaryBundleUUIDString. Bistream UUID: " + bitstream.getID() );
                UUID primaryBundleUUID = UUID.fromString(primaryBundleUUIDString);
                try {
                    Bundle primaryBundle = bundleService.find(context, primaryBundleUUID);
                    primaryBundle.setPrimaryBitstreamID(bitstream);
                    bundleService.update(context, primaryBundle);
                } catch (SQLException e) {
                    log.error("Something went wrong trying to find the Bundle with uuid: " +
                            primaryBundleUUID, e);
                }
            }
            log.info("Going to update bitstream with UUID: " + bitstream.getID());
            bitstreamService.update(context, bitstream);

            if (directBundleImport && bundleUUID != null) {
                String bitstreamOrderString = request.getParameter("bitstreamOrder");
                String legacyBitstreamOrderString = request.getParameter("legacyBitstreamOrder");
                if (StringUtils.isBlank(bitstreamOrderString)) {
                    throw new IllegalArgumentException(
                            "bitstreamOrder is required for the direct migration bundle path");
                }
                if (StringUtils.isBlank(legacyBitstreamOrderString)) {
                    throw new IllegalArgumentException(
                            "legacyBitstreamOrder is required for the direct migration bundle path");
                }
                clarinBitstreamService.addToBundleForMigration(
                        context,
                        bitstream,
                        bundleUUID,
                        Integer.parseInt(bitstreamOrderString),
                        Integer.parseInt(legacyBitstreamOrderString),
                        StringUtils.isNotBlank(primaryBundleUUIDString));
            }

            // If bitstream is deleted make it deleted
            if (deleted) {
                bitstreamService.delete(context, bitstream);
                log.info("Bitstream with id: " + bitstream.getID() + " is deleted!");
            }

            if (!Objects.isNull(bundle)) {
                List<Item> items = bundle.getItems();
                if (!items.isEmpty()) {
                    item = items.get(0);
                }
                if (item != null && !(authorizeService.authorizeActionBoolean(context, item, Constants.WRITE)
                        && authorizeService.authorizeActionBoolean(context, item, Constants.ADD))) {
                    log.info("You do not have write rights to update the Bundle's item.");
                    throw new AccessDeniedException("You do not have write rights to update the Bundle's item");
                }
                if (item != null && !itemFilesMetadataDeferred) {
                    // Update item file metadata after the bitstream size has changed
                    clarinItemService.updateItemFilesMetadata(context,
                            item, bundle);
                    itemService.update(context, item);
                }
                if (!itemFilesMetadataDeferred) {
                    bundleService.update(context, bundle);
                }
            }
            if (directBundleImport) {
                // The migrator consumes only the id. Avoid hydrating HAL links and
                // the just-mutated bundle collection for every imported row.
                bitstreamRest = new BitstreamRest();
                bitstreamRest.setUuid(bitstream.getID().toString());
            } else {
                bitstreamRest = converter.toRest(bitstream, utils.obtainProjection());
            }
            context.commit();
        } catch (Exception e) {
            String message = "Something went wrong with trying to create the single bitstream for file "
                    + "with internal_id: " + request.getParameter("internal_id");
            if (!Objects.isNull(bundle)) {
                message += " for bundle with uuid: " + bundle.getID();
            } else if (bundleUUID != null) {
                message += " for bundle with uuid: " + bundleUUID;
            }
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
        return bitstreamRest;
    }

    /**
     * Update bitstream checksum for bitstream, whose are not yet updated.
     * The mapping for requested endpoint, for example
     * <pre>
     * {@code
     * https://<dspace.server.url>/api/clarin/import/core/bitstream/checksum
     * }
     * </pre>
     * @param request request
     * @throws SQLException if database error
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(method =  RequestMethod.POST, value = "/bitstream/checksum")
    public void doUpdateBitstreamsChecksum(HttpServletRequest request) throws SQLException {
        Context context = obtainContext(request);
        if (Objects.isNull(context)) {
            throw new RuntimeException("Context is null!");
        }
        if (configurationService.getBooleanProperty("clarin.bitstream.validation.deferred", false)) {
            log.debug("Checksum registry initialization deferred until the post-migration fixity audit.");
            return;
        }
        checksumService.updateMissingBitstreams(context);
        context.commit();
    }

    /**
     * Convert String value to Integer.
     * @param value input value
     * @return input value converted to Integer
     */
    private Integer getIntegerFromString(String value) {
        Integer output = null;
        if (StringUtils.isNotBlank(value)) {
            output = Integer.parseInt(value);
        }
        return output;
    }
}

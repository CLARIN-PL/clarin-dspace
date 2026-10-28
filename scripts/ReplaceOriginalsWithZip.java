import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.*;
import org.dspace.core.*;
import org.dspace.content.*;
import org.dspace.content.service.*;
import org.dspace.content.service.clarin.*;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.handle.service.HandleService;
import org.dspace.utils.DSpace;

/** Retain source bundle and policies, expose one verified ZIP as ORIGINAL. */
public class ReplaceOriginalsWithZip {
    static <T> T service(Class<T> type) {
        return new DSpace().getServiceManager().getServicesByType(type).get(0);
    }
    static Set<String> policies(Context c, Bitstream b) throws Exception {
        return service(AuthorizeService.class).getPoliciesActionFilter(c, b, Constants.READ).stream()
            .map(p -> (p.getGroup() == null ? "" : p.getGroup().getID()) + ":"
                + (p.getEPerson() == null ? "" : p.getEPerson().getID()) + ":"
                + p.getStartDate() + ":" + p.getEndDate()).collect(Collectors.toSet());
    }
    static Set<Integer> licenses(Context c, Bitstream b) throws Exception {
        return service(ClarinLicenseResourceMappingService.class).findByBitstreamUUID(c, b.getID())
            .stream().map(m -> m.getLicense().getID()).collect(Collectors.toSet());
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("LOCAL-TRIAL", "PRODUCTION-20261028").contains(args[2])) {
            throw new IllegalArgumentException("handle zip LOCAL-TRIAL|PRODUCTION-20261028");
        }
        if (!Set.of("11321/222","11321/217","11321/756","11321/312","11321/238","11321/50",
                "11321/717","11321/215","11321/318","11321/539","11321/49","11321/319",
                "11321/592","11321/523","11321/534","11321/309").contains(args[0])) {
            throw new IllegalArgumentException("Handle outside approved migration list");
        }
        var kernel = org.dspace.servicemanager.DSpaceKernelInit.getKernel(null);
        if (!kernel.isRunning()) kernel.start();
        try (Context c = new Context()) {
            String ui = service(org.dspace.services.ConfigurationService.class).getProperty("dspace.ui.url");
            String expected = args[2].equals("LOCAL-TRIAL") ? "http://127.0.0.1:4400/dspace" : "https://clarin-pl.eu/dspace";
            if (ui == null || !ui.replaceAll("/$", "").equals(expected)) {
                throw new IllegalStateException("Unexpected deployment target");
            }
            // Validation is read-only: avoid a whole-session flush per query.
            c.setMode(Context.Mode.READ_ONLY);
            c.turnOffAuthorisationSystem();
            Item item = (Item) service(HandleService.class).resolveToObject(c, args[0]);
            if (item.getBundles("ZIP_SOURCE_BACKUP").size() > 0) throw new IllegalStateException("Already converted");
            List<Bundle> originals = new ArrayList<>(item.getBundles("ORIGINAL"));
            if (originals.size() != 1) throw new IllegalStateException("Expected one ORIGINAL bundle");
            Bundle old = originals.get(0);
            List<Bitstream> files = new ArrayList<>(old.getBitstreams());
            if (files.size() < 100) throw new IllegalStateException("Not a selected large resource");
            Set<String> rights = policies(c, files.get(0));
            Set<Integer> licenseIds = licenses(c, files.get(0));
            Map<String, Bitstream> byId = files.stream().collect(Collectors.toMap(Bitstream::getInternalId, b -> b));
            try (ZipFile zip = new ZipFile(args[1])) {
                JsonNode manifest = new ObjectMapper().readTree(zip.getInputStream(zip.getEntry("manifest.json")));
                if (manifest.size() != files.size()) throw new IllegalStateException("Manifest count mismatch");
                for (JsonNode row : manifest) {
                    Bitstream b = byId.remove(row.get("internal_id").asText());
                    if (b == null || !row.get("handle").asText().equals(args[0])
                        || b.getSizeBytes() != row.get("size_bytes").asLong()
                        || !Objects.equals(b.getName(), row.get("name").asText())
                        || !b.getChecksum().equalsIgnoreCase(row.get("checksum").asText())
                        || !rights.equals(policies(c,b)) || !licenseIds.equals(licenses(c,b)))
                        throw new IllegalStateException("Source or access policy mismatch");
                    java.security.MessageDigest digest = java.security.MessageDigest.getInstance(b.getChecksumAlgorithm());
                    try (var in = zip.getInputStream(zip.getEntry(row.get("entry").asText()))) {
                        byte[] buffer = new byte[1048576]; int n;
                        while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
                    }
                    if (!java.util.HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(b.getChecksum()))
                        throw new IllegalStateException("Archive checksum mismatch");
                }
            }
            if (!byId.isEmpty()) throw new IllegalStateException("Missing sources");
            c.setMode(Context.Mode.READ_WRITE);
            BundleService bundles = service(BundleService.class);
            BitstreamService bitstreams = service(BitstreamService.class);
            AuthorizeService auth = service(AuthorizeService.class);
            old.setName(c, "ZIP_SOURCE_BACKUP"); bundles.update(c, old);
            Bundle target = bundles.create(c, item, "ORIGINAL");
            Bitstream zip;
            try (var input = Files.newInputStream(Path.of(args[1]))) { zip = bitstreams.create(c, target, input); }
            zip.setName(c, args[0].replace('/', '-') + ".zip");
            bitstreams.setFormat(c, zip, service(BitstreamFormatService.class).findByMIMEType(c,"application/zip"));
            auth.removeAllPolicies(c, zip); auth.inheritPolicies(c, files.get(0), zip);
            for (Integer id : licenseIds) service(ClarinLicenseResourceMappingService.class).create(c, id, zip.getID());
            bitstreams.update(c, zip); target.setPrimaryBitstreamID(zip); bundles.update(c, target);
            service(ClarinItemService.class).updateItemFilesMetadata(c, item);
            service(ItemService.class).update(c, item);
            c.complete();
            System.out.println("CONVERTED " + args[0] + " zip=" + zip.getID() + " originalFiles=" + files.size());
        } finally { kernel.destroy(); }
    }
}

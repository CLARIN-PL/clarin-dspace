import java.util.*;
import org.dspace.core.*;
import org.dspace.content.*;
import org.dspace.content.service.*;
import org.dspace.versioning.*;
import org.dspace.versioning.service.*;
import org.dspace.xmlworkflow.storedcomponents.service.XmlWorkflowItemService;
import org.dspace.utils.DSpace;

/** One-off: link existing workflow 2, preserving its contents and review state. */
public class LinkDipssWorkflowVersion {
    static <T> T service(Class<T> type) {
        return new DSpace().getServiceManager().getServicesByType(type).get(0);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Set.of("check", "apply").contains(args[0])) throw new IllegalArgumentException();
        var kernel = org.dspace.servicemanager.DSpaceKernelInit.getKernel(null);
        if (!kernel.isRunning()) kernel.start();
        try (Context c = new Context()) {
            c.turnOffAuthorisationSystem();
            if (!"https://clarin-pl.eu/dspace".equals(service(org.dspace.services.ConfigurationService.class)
                    .getProperty("dspace.ui.url").replaceAll("/$", ""))) throw new IllegalStateException("Wrong target");
            ItemService items = service(ItemService.class);
            Item old = items.find(c, UUID.fromString("716db3b5-92aa-41e8-a495-5b1c4dc19286"));
            var workflow = service(XmlWorkflowItemService.class).find(c, 2);
            Item draft = workflow.getItem();
            if (!draft.getID().equals(UUID.fromString("a58dc5a7-417a-409e-a92d-f0f5b32ede8a"))
                    || !old.isArchived() || old.isWithdrawn() || draft.isArchived() || draft.isWithdrawn()
                    || !old.getOwningCollection().getID().equals(workflow.getCollection().getID())) {
                throw new IllegalStateException("Workflow or publication state changed");
            }
            VersionHistoryService histories = service(VersionHistoryService.class);
            VersioningService versions = service(VersioningService.class);
            if (histories.findByItem(c, old) != null || histories.findByItem(c, draft) != null) {
                throw new IllegalStateException("Version history already exists; review manually");
            }
            String uri = items.getMetadataFirstValue(old,"dc","identifier","uri",Item.ANY);
            if (uri == null || !uri.endsWith("11321/954")) throw new IllegalStateException("Unexpected original identifier");
            if (items.getMetadataFirstValue(draft,"dc","relation","replaces",Item.ANY) != null) {
                throw new IllegalStateException("Draft already replaces another item");
            }
            String accession = items.getMetadataFirstValue(old,"dc","date","accessioned",Item.ANY);
            Date firstDate = accession == null ? new Date() : new DCDate(accession).toDate();
            if (firstDate == null) throw new IllegalStateException("Invalid original accession date");
            VersionHistory history = histories.create(c);
            versions.createNewVersion(c, history, old, "", firstDate, 1);
            versions.createNewVersion(c, history, draft,
                "DiPSS part 2; existing workflow submission linked as a new version at repository administrator request.",
                new Date(), 2);
            items.addMetadata(c,draft,"dc","relation","replaces",null,uri);
            items.update(c,draft);
            System.out.println("Validated workflow=2 old=11321/954 draft=11321/1018 versions=1,2");
            if (args[0].equals("apply")) { c.complete(); System.out.println("COMMITTED history="+history.getID()); }
            else { c.abort(); System.out.println("CHECK ONLY: rolled back"); }
        } finally { kernel.destroy(); }
    }
}

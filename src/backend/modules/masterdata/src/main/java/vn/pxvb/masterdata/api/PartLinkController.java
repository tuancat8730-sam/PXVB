package vn.pxvb.masterdata.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.masterdata.application.PartLinkQueries;
import vn.pxvb.masterdata.application.PartLinkQueries.MachineOfPart;
import vn.pxvb.masterdata.application.PartLinkQueries.PartOfMachine;

/** Liên kết thiết bị–phụ tùng đọc hai chiều (FR-MD-019): phụ tùng của một máy và máy của một phụ tùng. */
@RestController
@RequestMapping("/api/v1/md")
public class PartLinkController {

    private final PartLinkQueries links;

    public PartLinkController(PartLinkQueries links) {
        this.links = links;
    }

    @GetMapping("/machines/{id}/parts")
    @PreAuthorize("hasAuthority('md.machine.view')")
    public List<PartOfMachine> partsOfMachine(@PathVariable UUID id) {
        return links.partsOfMachine(id);
    }

    @GetMapping("/spare-parts/{id}/machines")
    @PreAuthorize("hasAuthority('md.sparePart.view')")
    public List<MachineOfPart> machinesOfPart(@PathVariable UUID id) {
        return links.machinesOfPart(id);
    }
}

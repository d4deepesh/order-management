package com.example.ordermgmt.dto.response;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PAGED RESPONSE DTO
 *
 * CONCEPT: Wraps paginated data with metadata
 *
 * INTERVIEW POINTS:
 * - Page<T> from Spring Data has totalElements, totalPages etc.
 * - We map it to this custom DTO for clean API contract
 * - XML needs a wrapper object -- List<T> directly is invalid XML
 *  (XML must have exactly one root element)
 *
 * @JacksonXmlRootElement -- root XML element name
 * @JacksonXmlElementWrapper -- wraps list in <content> element in XML
 * @JacksonXmlProperty     -- each list item element name in XML
 * JSON output:
 * { "content": [...], "page": 0, "totalElements": 47 }
 *
 * XML output:
 * <pagedOrders>
 *   <content><order>...</order></content>
 *   <page>0</page>
 *   <totalElements>47</totalElements>
 * </pagedOrders>
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "pagedOrders")
public class PagedResponse<T> {

    // actual data for this page
    @JacksonXmlElementWrapper(localName = "content")
    @JacksonXmlProperty(localName = "order")
    private List<T> content;

    // current page number (0-based)
    private int page;

    // page size requested
    private int size;

    // total number of matching records across all pages
    private long totalElements;

    // total number of pages
    private int totalPages;

    // is this the first page?
    private boolean first;

    // is this the last page?
    private boolean last;

    // are there more pages?
    private boolean hasNext;

    // are there previous pages?
    private boolean hasPrevious;
}

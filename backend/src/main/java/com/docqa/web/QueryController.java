package com.docqa.web;

import com.docqa.rag.QueryService;
import com.docqa.security.AuthUser;
import com.docqa.web.Dtos.QueryRequest;
import com.docqa.web.Dtos.QueryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {
    private final QueryService queries;

    @PostMapping
    public QueryResponse ask(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody QueryRequest req) {
        return queries.query(user.id(), req.question().trim());
    }
}

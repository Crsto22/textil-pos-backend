package com.sistemapos.sistematextil.services.agent.fulfillment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.ShalomAgency;
import com.sistemapos.sistematextil.repositories.ShalomAgencyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShalomAgencySearchService {
    private static final Set<String> QUERY_NOISE = Set.of(
            "agencia", "agencias", "shalom", "sede", "destino", "envio", "en", "de", "la", "el");
    private final ShalomAgencyRepository repository;

    @Transactional(readOnly = true)
    public SearchResult search(String query) {
        String normalized = cleanQuery(ShalomAgencySyncService.normalize(query));
        if (normalized.length() < 2) return new SearchResult("QUERY_REQUIRED", List.of());
        List<Scored> scored = new ArrayList<>();
        for (ShalomAgency agency : repository.findByActiveTrueAndDestinationEnabledTrueOrderByNameAsc()) {
            double score = score(normalized, agency);
            if (score >= 0.28) scored.add(new Scored(agency, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(item -> item.agency().getName()));
        boolean clearMatch = !scored.isEmpty() && (scored.size() == 1
                || scored.get(0).score() - scored.get(1).score() >= .18);
        List<AgencyView> candidates = scored.stream().limit(clearMatch ? 1 : 3)
                .map(item -> view(item.agency(), item.score())).toList();
        String status = candidates.isEmpty() ? "NOT_FOUND" : clearMatch ? "MATCH" : "AMBIGUOUS";
        return new SearchResult(status, candidates);
    }

    @Transactional(readOnly = true)
    public AgencyView get(Long id) {
        ShalomAgency agency = repository.findById(id)
                .filter(ShalomAgency::isActive).filter(ShalomAgency::isDestinationEnabled)
                .orElseThrow(() -> new IllegalArgumentException("La agencia Shalom no esta disponible"));
        return view(agency, 1);
    }

    /**
     * Resuelve una referencia natural exclusivamente contra las agencias que ya
     * fueron ofrecidas. El orden de las palabras y expresiones genéricas como
     * "agencia", "sede" o "Shalom" no afectan la coincidencia.
     */
    @Transactional(readOnly = true)
    public ReferenceResolution resolveReference(String reference, List<Long> offeredIds) {
        String normalized = cleanQuery(ShalomAgencySyncService.normalize(reference));
        if (normalized.length() < 2 || offeredIds == null || offeredIds.isEmpty()) {
            return new ReferenceResolution("NO_MATCH", null, List.of());
        }
        List<Scored> matches = offeredIds.stream().distinct()
                .map(repository::findById).flatMap(java.util.Optional::stream)
                .filter(ShalomAgency::isActive).filter(ShalomAgency::isDestinationEnabled)
                .map(agency -> new Scored(agency, score(normalized, agency)))
                .filter(item -> item.score() >= .62)
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(item -> item.agency().getName()))
                .toList();
        if (matches.isEmpty()) return new ReferenceResolution("NO_MATCH", null, List.of());
        boolean unique = matches.size() == 1 || matches.get(0).score() - matches.get(1).score() >= .18;
        List<AgencyView> candidates = matches.stream().limit(unique ? 1 : 3)
                .map(item -> view(item.agency(), item.score())).toList();
        return new ReferenceResolution(unique ? "MATCH" : "AMBIGUOUS",
                unique ? candidates.getFirst().agencyId() : null, candidates);
    }

    private double score(String query, ShalomAgency agency) {
        String locality = ShalomAgencySyncService.normalize(String.join(" ",
                safe(agency.getName()), safe(agency.getDepartment()), safe(agency.getProvince()),
                safe(agency.getZone()), safe(agency.getPlace()), safe(agency.getAbbreviation())));
        String full = ShalomAgencySyncService.normalize(safe(agency.getNormalizedSearch()));
        return Math.max(scoreText(query, locality), scoreText(query, full) * .78);
    }

    private double scoreText(String query, String candidate) {
        if (candidate.equals(query)) return 1;
        double contains = candidate.contains(query) ? .92 : query.contains(candidate) ? .82 : 0;
        Set<String> q = new HashSet<>(List.of(query.split(" ")));
        Set<String> c = new HashSet<>(List.of(candidate.split(" ")));
        int matches = 0;
        for (String token : q) {
            if (c.contains(token)) { matches++; continue; }
            if (token.length() >= 4 && c.stream().anyMatch(other -> equivalentToken(token, other))) matches++;
        }
        double coverage = q.isEmpty() ? 0 : (double) matches / q.size();
        String lastToken = query.substring(query.lastIndexOf(' ') + 1);
        double specificTokenBonus = lastToken.length() >= 4 && c.contains(lastToken) ? .22 : 0;
        return Math.min(1, Math.max(contains, coverage * .88) + specificTokenBonus);
    }

    private String safe(String value) { return value == null ? "" : value; }

    private boolean equivalentToken(String left, String right) {
        int shortest = Math.min(left.length(), right.length());
        return similarity(left, right) >= .78
                || shortest >= 5 && (left.startsWith(right) || right.startsWith(left));
    }

    private String cleanQuery(String value) {
        return java.util.Arrays.stream(value.split(" "))
                .filter(token -> !token.isBlank() && !QUERY_NOISE.contains(token))
                .reduce((left, right) -> left + " " + right).orElse("");
    }

    private double similarity(String left, String right) {
        int max = Math.max(left.length(), right.length());
        return max == 0 ? 1 : 1d - ((double) distance(left, right) / max);
    }

    private int distance(String a, String b) {
        int[] previous = new int[b.length()+1]; for(int j=0;j<=b.length();j++)previous[j]=j;
        for(int i=1;i<=a.length();i++){int[] current=new int[b.length()+1];current[0]=i;
            for(int j=1;j<=b.length();j++)current[j]=Math.min(Math.min(current[j-1]+1,previous[j]+1),previous[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
            previous=current;} return previous[b.length()];
    }
    private AgencyView view(ShalomAgency a,double score){return new AgencyView(a.getIdShalomAgency(),a.getExternalId(),a.getName(),
            a.getAbbreviation(),a.getDepartment(),a.getProvince(),a.getPlace(),a.getAddress(),a.getPhone(),a.getAttentionHours(),score);}
    private record Scored(ShalomAgency agency,double score){}
    public record SearchResult(String status,List<AgencyView> candidates){}
    public record ReferenceResolution(String status,Long agencyId,List<AgencyView> candidates){}
    public record AgencyView(Long agencyId,Integer externalId,String name,String abbreviation,String department,
            String province,String place,String address,String phone,String attentionHours,double score){}
}

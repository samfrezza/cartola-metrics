package br.com.cartola.metrics.service;

import br.com.cartola.metrics.model.Clube;
import br.com.cartola.metrics.model.Partida;
import br.com.cartola.metrics.model.Rodada;
import br.com.cartola.metrics.model.RodadaClube;
import br.com.cartola.metrics.repository.ClubeRepository;
import br.com.cartola.metrics.repository.RodadaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Component
public class RodadaTotalService {

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private ClubeRepository clubeRepo;
    @Autowired
    private RodadaRepository rodadaRepo;

    public void totalizarRodada(Integer rodadaId) {
        Aggregation agg = newAggregation(
                match(where("_id").is(rodadaId)),
                project("atletas"),
                unwind("atletas"),
                group("atletas.clube_id").sum("atletas.pontos_num").as("total")
        );

        AggregationResults<RodadaTotalResults> groupResults = mongoTemplate.aggregate(agg, Rodada.class, RodadaTotalResults.class);
        List<RodadaTotalResults> results = groupResults.getMappedResults();

        List<Clube> clubes = clubeRepo.findAll();
        Rodada rodadaAtual = rodadaRepo.findOne(rodadaId);

        clubes.forEach(c -> {
            RodadaTotalResults rodadaTotal = results.stream().filter(r -> Objects.equals(r.getId(), c.getId())).findFirst().get();
            RodadaClube rodadaClube = new RodadaClube();
            rodadaClube.setId(rodadaId);
            rodadaClube.setPontos(rodadaTotal.getTotal());

            Partida part = findPartida(rodadaAtual, c);
            rodadaClube.setValida(part.getValida());
            rodadaClube.setCasa(Objects.equals(part.getClube_casa_id(), c.getId()));

            rodadaClube.setPontosCedidos(getPontosCedidos(results, rodadaClube.getCasa(), part));

            adicionaRodada(c, rodadaClube);

            clubeRepo.save(c);
        });
    }

    private void adicionaRodada(Clube clube, RodadaClube rodada) {
        if (clube.getRodadas() == null) {
            clube.setRodadas(new ArrayList<>());
        }
        clube.getRodadas().removeIf(i -> Objects.equals(i.getId(), rodada.getId()));
        clube.getRodadas().add(rodada);
    }

    private Partida findPartida(Rodada rodada, Clube clube) {
        return rodada.getPartidas().stream()
                .filter(p -> Objects.equals(p.getClube_casa_id(), clube.getId())
                        || Objects.equals(p.getClube_visitante_id(), clube.getId()))
                .findFirst().get();
    }

    private Double getPontosCedidos(List<RodadaTotalResults> resultados, Boolean casa, Partida partida) {
        Integer adversarioId = casa ? partida.getClube_visitante_id() : partida.getClube_casa_id();
        return resultados.stream()
                .filter(r -> Objects.equals(r.getId(), adversarioId))
                .findFirst().get().getTotal();
    }
}

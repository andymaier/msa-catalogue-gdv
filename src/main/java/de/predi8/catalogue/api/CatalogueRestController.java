package de.predi8.catalogue.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.predi8.catalogue.error.NotFoundException;
import de.predi8.catalogue.event.Operation;
import de.predi8.catalogue.model.Article;
import de.predi8.catalogue.repository.ArticleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;


@RestController
@RequestMapping("/articles")
public class CatalogueRestController {

	private ArticleRepository repo;
	private KafkaTemplate<String, Operation> kafka;
	final private ObjectMapper mapper;

	public CatalogueRestController(ArticleRepository repo, KafkaTemplate<String, Operation> kafka,
			ObjectMapper mapper) {
		this.repo = repo;
		this.kafka = kafka;
		this.mapper = mapper;
	}

	@GetMapping
	public List<Article> index() {
		return repo.findAll();
	}

	@GetMapping("/count")
	public long count() {
		return repo.count();
	}

	@GetMapping("/{id}")
	public Article get(@PathVariable String id) {
		return repo.findById(id).orElseThrow(NotFoundException::new);
	}

	@PostMapping
	public ResponseEntity<Article> createArticle(@RequestBody Article article, UriComponentsBuilder builder) {
		System.out.println("Article: " + article.toString());
		String id = UUID.randomUUID().toString();
		article.setUuid(id);

		Operation op = new Operation("article", "upsert", mapper.valueToTree(article));
		kafka.send("shop", op);

		//Article newArticle = repo.save(article);
		//return ResponseEntity.created(builder.path("/articles/" + id).build().toUri()).body(newArticle);
		return ResponseEntity.accepted().build();
	}

	@PutMapping("/{id}")
	public ResponseEntity<Article> updateArticle(@PathVariable String id, @RequestBody Article article) throws NotFoundException {
		if (!repo.existsById(id))
			throw new NotFoundException();
		article.setUuid(id);
		Operation op = new Operation("article", "upsert", mapper.valueToTree(article));
		kafka.send("shop", op);
		return ResponseEntity.accepted().build();
	}

	@PatchMapping("/{id}")
	public ResponseEntity<Article> patch(@PathVariable String id, @RequestBody JsonNode json) {
		Article old = get(id);
		// JSON 3 Zustände: kein Attribut, null, Wert

		if(json.hasNonNull("uuid")) old.setUuid(json.get("uuid").asText());

		if (json.has("price")) {
			if (json.hasNonNull("price")) {
				old.setPrice( new BigDecimal( json.get("price").asDouble()));
			}
		}
		
		if ( json.has("name")) {
			old.setName( json.get("name").asText());
		}

		Operation op = new Operation("article", "upsert", mapper.valueToTree(old));
		kafka.send("shop", op);
		//return repo.save(old);
		return ResponseEntity.accepted().build();
	}

	@DeleteMapping("/{id}")
	public void del(@PathVariable String id) {
		Operation op = new Operation("article", "delete", mapper.valueToTree(get(id)));
		kafka.send("shop", op);
	}
}
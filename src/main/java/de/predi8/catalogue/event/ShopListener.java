package de.predi8.catalogue.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.predi8.catalogue.model.Article;
import de.predi8.catalogue.model.ObjectError;
import de.predi8.catalogue.repository.ArticleRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShopListener {
	private final ObjectMapper mapper;
	private final ArticleRepository repo;
	private final NullAwareBeanUtilsBean beanUtils;
	private final KafkaTemplate<String, Operation> kafka;

	public ShopListener(ObjectMapper mapper, ArticleRepository repo, NullAwareBeanUtilsBean beanUtils, KafkaTemplate<String, Operation> kafka) {
		this.mapper = mapper;
		this.repo = repo;
		this.beanUtils = beanUtils;
		this.kafka = kafka;
	}

	@KafkaListener(topics = "shop")
	public void listen(Operation op) throws Exception {
		System.out.println("op = " + op);

		Article article = mapper.treeToValue(op.getObject(), Article.class);

		switch (op.getAction()) {

			case "upsert":
				repo.save(article);
				break;
			case "delete":
				repo.delete(article);
				break;
			default:
				ObjectError oenew = new ObjectError("Error", mapper.writeValueAsString(op));
				Operation opnew = new Operation("obejct_error", "error_catalogue", mapper.valueToTree(oenew));
				kafka.send("shop_error", opnew);
		}
	}
}
package com.agorapulse.micronaut.aws.dynamodb.builder

import com.agorapulse.micronaut.aws.dynamodb.DynamoDBMetadata
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.datamodeling.IDynamoDBMapper
import com.amazonaws.services.dynamodbv2.model.Condition
import com.amazonaws.services.dynamodbv2.model.ConditionalOperator
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import io.reactivex.Flowable

import java.util.function.BiConsumer
import java.util.function.Consumer

@PackageScope
@CompileStatic
class DefaultQueryBuilder<T> implements QueryBuilder<T> {

    DefaultQueryBuilder(Class<T> type, DynamoDBQueryExpression<T> expression) {
        this.metadata = DynamoDBMetadata.create(type)
        this.expression = expression
    }

    DefaultQueryBuilder<T> sort(Builders.Sort sort) {
        if (sort == Builders.Sort.ASC) {
            expression.withScanIndexForward(true)
        } else if (sort == Builders.Sort.DESC) {
            expression.withScanIndexForward(false)
        }
        return this
    }

    DefaultQueryBuilder<T> inconsistent(Builders.Read read) {
        if (read.equals(Builders.Read.READ)) {
            expression.withConsistentRead(false)
        }

        return this
    }

    DefaultQueryBuilder<T> consistent(Builders.Read read) {
        if (read.equals(Builders.Read.READ)) {
            expression.withConsistentRead(true)
        }

        return this
    }

    DefaultQueryBuilder<T> index(String name) {
        expression.withIndexName(name)
        return this
    }

    DefaultQueryBuilder<T> hash(Object hash) {
        this.hashKey = hash
        return this
    }

    DefaultQueryBuilder<T> range(Consumer<RangeConditionCollector<T>> conditions) {
        rangeCollectorsConsumers.add(conditions)
        return this
    }

    DefaultQueryBuilder<T> filter(Consumer<RangeConditionCollector<T>> conditions) {
        filterCollectorsConsumers.add(conditions)
        return this
    }

    DefaultQueryBuilder<T> filter(ConditionalOperator or) {
        expression.withConditionalOperator(or)
        return this
    }

    DefaultQueryBuilder<T> page(int page) {
        expression.withLimit(page)
        return this
    }

    DefaultQueryBuilder<T> offset(Object exclusiveStartKeyValue) {
        this.exclusiveStartKey = exclusiveStartKeyValue
        return this
    }

    @Override
    int count(IDynamoDBMapper mapper) {
        return mapper.count(metadata.getItemClass(), resolveExpression(mapper))
    }

    @Override
    Flowable<T> query(IDynamoDBMapper mapper) {
        return FlowableQueryResultHelper.generate(metadata.itemClass, mapper, resolveExpression(mapper))
    }

    @Override
    DynamoDBQueryExpression<T> resolveExpression(IDynamoDBMapper mapper) {
        DynamoDBMapperTableModel<T> model = mapper.getTableModel(metadata.getItemClass())

        if (hashKey != null) {
            expression.withHashKeyValues(model.createKey(hashKey, null))
        }


        applyConditions(model, rangeCollectorsConsumers, expression.&withRangeKeyCondition)
        applyConditions(model, filterCollectorsConsumers, expression.&withQueryFilterEntry)


        if (exclusiveStartKey != null) {
            T exclusiveKey = model.createKey(exclusiveStartKey, null)
            expression.withExclusiveStartKey(model.convertKey(exclusiveKey))
        }

        configurer.accept(expression)

        return expression
    }

    @Override
    QueryBuilder<T> only(Iterable<String> propertyPaths) {
        expression.projectionExpression = propertyPaths.join(',')
        return this
    }

    @Override
    QueryBuilder<T> configure(Consumer<DynamoDBQueryExpression<T>> configurer) {
        this.configurer = configurer
        return this
    }

    // for proper groovy evaluation of closure in the annotation
    Object getProperty(String name) {
        throw new MissingPropertyException("No properties here!")
    }

    private void applyConditions(DynamoDBMapperTableModel<T> model, List<Consumer<RangeConditionCollector<T>>> filterCollectorsConsumers, BiConsumer<String, Condition> addFilterConsumer) {
        if (!filterCollectorsConsumers.isEmpty()) {
            RangeConditionCollector<T> filterCollector = new RangeConditionCollector<>(model)

            for (Consumer<RangeConditionCollector<T>> consumer : filterCollectorsConsumers) {
                consumer.accept(filterCollector)
            }

            filterCollector.getConditions().forEach(addFilterConsumer)
        }
    }

    private final DynamoDBMetadata<T> metadata
    private final DynamoDBQueryExpression<T> expression
    private Object hashKey
    private Object exclusiveStartKey
    private List<Consumer<RangeConditionCollector<T>>> rangeCollectorsConsumers = new ArrayList<>()
    private List<Consumer<RangeConditionCollector<T>>> filterCollectorsConsumers = new ArrayList<>()
    private Consumer<DynamoDBQueryExpression<T>> configurer = {} as Consumer<DynamoDBQueryExpression<T>>
}
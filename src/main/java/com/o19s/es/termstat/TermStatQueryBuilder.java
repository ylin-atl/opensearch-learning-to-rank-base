/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper.AggrType;

import com.o19s.es.ltr.utils.Scripting;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.opensearch.core.ParseField;
import org.opensearch.core.common.ParsingException;
import org.opensearch.core.common.io.stream.NamedWriteable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ObjectParser;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class TermStatQueryBuilder extends AbstractQueryBuilder<TermStatQueryBuilder> implements NamedWriteable {
    public static final String NAME = "term_stat";

    private static final ParseField EXPR_NAME = new ParseField("expr");
    private static final ParseField AGGR_NAME = new ParseField("aggr");
    private static final ParseField POS_AGGR_NAME = new ParseField("pos_aggr");

    private static final ParseField TERMS_NAME = new ParseField("terms");
    private static final ParseField FIELDS_NAME = new ParseField("fields");
    private static final ParseField ANALYZER_NAME = new ParseField("analyzer");

    private static final ObjectParser<TermStatQueryBuilder, Void> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME, TermStatQueryBuilder::new);
        PARSER.declareStringArray(TermStatQueryBuilder::terms, TERMS_NAME);
        PARSER.declareStringArray(TermStatQueryBuilder::fields, FIELDS_NAME);
        PARSER.declareStringOrNull(TermStatQueryBuilder::analyzer, ANALYZER_NAME);
        PARSER.declareString(TermStatQueryBuilder::expr, EXPR_NAME);
        PARSER.declareString(TermStatQueryBuilder::aggr, AGGR_NAME);
        PARSER.declareStringOrNull(TermStatQueryBuilder::posAggr, POS_AGGR_NAME);
        declareStandardFields(PARSER);
    }

    private String[] terms;
    private String[] fields;
    private String analyzerName;
    private String expr;
    private String aggr;
    private String pos_aggr;


    public TermStatQueryBuilder() {
    }

    public TermStatQueryBuilder(StreamInput in) throws IOException {
        super(in);
        expr = in.readString();
        aggr = in.readString();
        pos_aggr = in.readOptionalString();
        terms = in.readStringArray();
        fields = in.readStringArray();
        analyzerName = in.readOptionalString();
    }

    public static TermStatQueryBuilder fromXContent(XContentParser parser) throws IOException {
        final TermStatQueryBuilder builder;

        try {
            builder = PARSER.parse(parser, null);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }

        if (builder.terms == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + TERMS_NAME + "] is mandatory");
        }

        if (builder.expr == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + EXPR_NAME + "] is mandatory");
        }

        // Default to all fields if none specified
        if (builder.fields == null) {
            builder.fields(new String[]{"*"});
        }

        // Default aggr to mean if none specified
        if (builder.aggr == null) {
            builder.aggr(AggrType.AVG.getType());
        }

        // Default pos_aggr to mean if none specified
        if (builder.pos_aggr == null) {
            builder.posAggr(AggrType.AVG.getType());
        }

        return builder;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(expr);
        out.writeString(aggr);
        out.writeOptionalString(pos_aggr);
        out.writeStringArray(terms);
        out.writeStringArray(fields);
        out.writeOptionalString(analyzerName);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        printBoostAndQueryName(builder);
        builder.field(EXPR_NAME.getPreferredName(), expr);
        builder.field(AGGR_NAME.getPreferredName(), aggr);
        builder.field(POS_AGGR_NAME.getPreferredName(), pos_aggr);
        builder.array(TERMS_NAME.getPreferredName(), terms);
        builder.array(FIELDS_NAME.getPreferredName(), fields);
        builder.field(ANALYZER_NAME.getPreferredName(), analyzerName);

        builder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        Expression compiledExpression = (Expression) Scripting.compile(expr);
        AggrType aggrType = AggrType.valueOf(aggr.toUpperCase(Locale.getDefault()));
        AggrType posAggrType = AggrType.valueOf(pos_aggr.toUpperCase(Locale.getDefault()));

        Analyzer analyzer = null;
        Set<Term> termSet = new HashSet<>();
        for (String field : fields) {
            // If no analyzer was specified, try grabbing it per field
            if (analyzerName == null) {
                analyzer = getAnalyzerForField(context, field);
            // Otherwise use the requested analyzer
            } else if (analyzer == null){
                analyzer = getAnalyzerByName(context, analyzerName);
            }

            if (analyzer == null) {
                throw new IllegalArgumentException("No analyzer found for [" + analyzerName + "]");
            }

            for (String termString : terms) {
                TokenStream ts = analyzer.tokenStream(field, termString);
                TermToBytesRefAttribute termAtt = ts.getAttribute(TermToBytesRefAttribute.class);

                ts.reset();
                while (ts.incrementToken()) {
                    termSet.add(new Term(field, termAtt.getBytesRef()));
                }
                ts.close();
            }
        }

        return new TermStatQuery(compiledExpression, aggrType, posAggrType, termSet);
    }

    private Analyzer getAnalyzerForField(QueryShardContext context, String fieldName) {
        MappedFieldType fieldType = context.getMapperService().fieldType(fieldName);
        return context.getSearchAnalyzer(fieldType);
    }

    private Analyzer getAnalyzerByName(QueryShardContext context, String analyzerName) {
        return context.getMapperService().getIndexAnalyzers().get(analyzerName);
    }

    @Override
    protected int doHashCode() { return Objects.hash(expr, aggr, pos_aggr, Arrays.hashCode(terms), Arrays.hashCode(fields), analyzerName);}

    @Override
    protected boolean doEquals(TermStatQueryBuilder other) {
        return Objects.equals(expr, other.expr)
                && Objects.equals(aggr, other.aggr)
                && Objects.equals(pos_aggr, other.pos_aggr)
                && Arrays.equals(terms, other.terms)
                && Arrays.equals(fields, other.fields)
                && Objects.equals(analyzerName, other.analyzerName);
    }

    @Override
    public String getWriteableName() { return NAME; }


    public String aggr() {
        return aggr;
    }
    public TermStatQueryBuilder aggr(String aggr) {
        this.aggr = aggr;
        return this;
    }

    public String analyzer() { return analyzerName; }
    public TermStatQueryBuilder analyzer(String analyzer) {
        this.analyzerName = analyzer;
        return this;
    }

    public String expr() {
        return expr;
    }
    public TermStatQueryBuilder expr(String expr) {
        this.expr = expr;
        return this;
    }

    public String[] fields() {
        return fields;
    };
    public TermStatQueryBuilder fields(String[] fields) {
        this.fields = fields;
        return this;
    }
    public TermStatQueryBuilder fields(List<String> text) {
        this.fields = text.toArray(new String[]{});
        return this;
    }


    public String posAggr() {
        return pos_aggr;
    }
    public TermStatQueryBuilder posAggr(String pos_aggr) {
        this.pos_aggr = pos_aggr;
        return this;
    }

    public String[] terms() { return terms; }
    public TermStatQueryBuilder terms(String[] terms) {
        this.terms = terms;
        return this;
    }
    public TermStatQueryBuilder terms(List<String> terms) {
        this.terms = terms.toArray(new String[]{});
        return this;
    }
}

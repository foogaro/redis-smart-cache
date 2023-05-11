package com.redis.smartcache.cli.commands;

import com.redis.smartcache.cli.Constants;
import com.redis.smartcache.cli.RedisService;
import com.redis.smartcache.cli.components.ConfirmationInputExtension;
import com.redis.smartcache.cli.components.StringInputExtension;
import com.redis.smartcache.cli.components.TableSelector;
import com.redis.smartcache.cli.structures.*;
import com.redis.smartcache.cli.util.Util;
import com.redis.smartcache.core.Config.RuleConfig;
import org.jline.utils.InfoCmp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.component.ConfirmationInput;
import org.springframework.shell.component.StringInput;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import io.airlift.units.Duration;
import java.util.*;
import java.util.stream.Collectors;

@ShellComponent
public class Commands extends AbstractShellComponent {
    final String QUERY_IDS = "Query IDs";
    final String TABLES = "Tables";
    final String TABLES_ANY = "Tables Any";
    final String TABLES_ALL = "Tables All";
    final String REGEX = "Regex";
    final String LIST_APPLICATION_QUERIES = "List application queries";
    final String CREATE_RULE = "Create query caching rule";
    final String LIST_TABLES = "List Tables";
    final String LIST_RULES = "List Rules";
    final String EXIT = "Exit";
    final String[] ruleTypes = {QUERY_IDS, TABLES, TABLES_ANY, TABLES_ALL, REGEX};

    final String tableInstructions = "press 'enter' to edit\npress 'c' to commit\npress 'esc' to go back\npress ctrl+c to exit\n";

    @Autowired
    private ComponentFlow.Builder componentFlowBuilder;

    @Autowired
    RedisService client;

    @ShellMethod(key = "ping", value = "ping")
    String ping(){
        return client.ping();
    }

    public Optional<RuleTypeInfo> getRuleType(){
        List<SelectorItem<RuleTypeInfo>> ruleTypes = Arrays.asList(
                SelectorItem.of(RuleType.TABLES.getValue(), new RuleTypeInfo(RuleType.TABLES,"Enter a comma-separated list of tables to match against:")),
                SelectorItem.of(RuleType.TABLES_ALL.getValue(), new RuleTypeInfo(RuleType.TABLES_ALL,"Enter a comma-separated list of tables to match against:")),
                SelectorItem.of(RuleType.TABLES_ANY.getValue(), new RuleTypeInfo(RuleType.TABLES_ANY,"Enter a comma-separated list of tables to match against:")),
                SelectorItem.of(RuleType.QUERY_IDS.getValue(), new RuleTypeInfo(RuleType.QUERY_IDS,"Enter a comma-separated list of Query IDs to match against:")),
                SelectorItem.of(RuleType.REGEX.getValue(), new RuleTypeInfo(RuleType.REGEX,"Enter a regular expression to match against:"))
        );

        TableSelector<RuleTypeInfo, SelectorItem<RuleTypeInfo>> component = new TableSelector<RuleTypeInfo, SelectorItem<RuleTypeInfo>>(getTerminal(),
                ruleTypes, "rules", null, "Select Rule Type", true, 1, "");
        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());
        TableSelector.SingleItemSelectorContext<RuleTypeInfo, SelectorItem<RuleTypeInfo>> context = component
                .run(TableSelector.SingleItemSelectorContext.empty(1, ""));
        if(!component.isEscapeMode()){
            return Optional.of(context.getResultItem().get().getItem());
        }

        return Optional.empty();
    }

    public Optional<Duration> getTtl(String message){
        String prompt;
        if(message.isEmpty()){
            prompt = "Enter a TTL in the form of a duration (e.g. 1h, 300s, 5m):";
        }
        else{
            prompt = String.format("%s%nEnter a TTL in the form of a duration (e.g. 1h, 300s, 5m):",message);
        }

        StringInputExtension stringInputComponent = new StringInputExtension(getTerminal(), prompt,"30m");
        stringInputComponent.setResourceLoader(getResourceLoader());
        stringInputComponent.setTemplateExecutor(getTemplateExecutor());
        StringInput.StringInputContext stringInputContext = stringInputComponent.run(StringInput.StringInputContext.empty());
        if(stringInputComponent.isEscapeMode()){
            return Optional.empty();
        }

        return Optional.of(Duration.valueOf(stringInputContext.getResultValue()));
    }

    public Optional<String> getMatch(RuleTypeInfo ruleType){
        StringInputExtension component = new StringInputExtension(getTerminal(),ruleType.getMessage(),"");
        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());
        StringInput.StringInputContext context = component.run(StringInput.StringInputContext.empty());
        if(component.isEscapeMode()){
            return Optional.empty();
        }

        return Optional.of(context.getResultValue());
    }

    public Optional<Boolean> getConfirmation(RuleInfo info){
        String prompt = String.format("Rule Type: %s, Rule Match: %s, Rule TTL: %s", info.ruleType(), info.ruleMatch(), info.getRule().getTtl());
        ConfirmationInputExtension component = new ConfirmationInputExtension(getTerminal(), prompt, false);
        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());
        ConfirmationInput.ConfirmationInputContext context = component.run(ConfirmationInput.ConfirmationInputContext.empty());
        if(component.isEscapeMode()){
            return Optional.empty();
        }

        return Optional.of(context.getResultValue());
    }

    public Optional <RuleConfig> newRuleDialogCustom(boolean confirm){
        Optional<Boolean> confirmed = Optional.empty();
        Optional<RuleTypeInfo> ruleType = Optional.empty();
        Optional<Duration> ttl = Optional.empty();
        Optional<String> match = Optional.empty();
        Optional<RuleConfig> rule = Optional.empty();
        do{
            ruleType = getRuleType();
            if(!ruleType.isPresent()){
                break;
            }

            do{
                match = getMatch(ruleType.get());
                if(!match.isPresent()){
                    break;
                }

                ttl = getTtl("");
            } while(!ttl.isPresent());

            if(!match.isPresent()){
                continue;
            }

            rule = Optional.of(Util.createRule(ruleType.get().getType(), match.get(),ttl.get()));

            if(confirm){
                confirmed = getConfirmation(new RuleInfo(rule.get(), RuleInfo.Status.New));
                if(confirmed.isPresent() && !confirmed.get()){
                    break;
                }
            }

        }while(!confirmed.isPresent() && confirm);

        if(confirmed.isPresent() && confirmed.get()){
            return rule;
        }

        return Optional.empty();
    }

    public Optional<RuleConfig> newRuleDialog(boolean confirm){
        List<SelectorItem<String>> options = new ArrayList<>();
        for(String option : ruleTypes){
            options.add(SelectorItem.of(option, option));
        }

        Map<String,String> map = Arrays.stream(ruleTypes).collect(Collectors.toMap(str->str,str->str, (oldValue,newValue)->oldValue, HashMap::new));

        Optional<RuleTypeInfo> ruleTypeInfo = getRuleType();



        ComponentFlow flow = componentFlowBuilder.clone().reset()
                .withSingleItemSelector("ruleType")
                .name("Select Rule Type")
                .selectItems(map)
                .next(ctx->ctx.getResultItem().get().getItem())
                .and()
                .withStringInput(TABLES)
                .name("Enter a comma-separated list of tables to match against:")
                .next(ctx->"TTL")
                .and()
                .withStringInput(TABLES_ANY)
                .name("Enter a comma-separated list of tables to match against:")
                .next(x->"TTL")
                .and()
                .withStringInput(TABLES_ALL)
                .name("Enter a comma-separated list of tables to match against:")
                .next(x->"TTL")
                .and()
                .withStringInput(QUERY_IDS)
                .name("Enter a comma-separated list of Query IDs to match against:")
                .next(x->"TTL")
                .and()
                .withStringInput(REGEX)
                .name("Enter a regular expression to match against:")
                .next(x->"TTL")
                .and()
                .withStringInput("TTL")
                .name("Enter a TTL in the form of a duration (e.g. 1h, 300s, 5m):")
                .next(x-> confirm ? "Confirm" : null)
                .and()
                .withConfirmationInput("Confirm")
                .name("Would you like to commit this new rule?")
                .next(null)
                .template("classpath:confirmation-input.stg")
                .and()
                .templateExecutor(getTemplateExecutor())
                .resourceLoader(getResourceLoader())
                .build();

        ComponentFlow.ComponentFlowResult res = flow.run();

        String ruleType = res.getContext().get("ruleType");
        String match = res.getContext().get(ruleType);
        String ttl = res.getContext().get("TTL");
        boolean confirmed = !confirm || (boolean)res.getContext().get("Confirm");

        if(confirmed){
            List<RuleConfig> rules = client.getRules();

            RuleConfig.Builder builder = new RuleConfig.Builder().ttl(Duration.valueOf(ttl));
            switch (ruleType){
                case QUERY_IDS:
                    builder.queryIds(match);
                    break;
                case TABLES_ANY:
                    builder.tablesAny(match);
                    break;
                case TABLES:
                    builder.tables(match);
                    break;
                case TABLES_ALL:
                    builder.tablesAll(match);
                    break;
                case REGEX:
                    builder.regex(match);
                    break;
            }

            return Optional.of(builder.build());
        }

        return Optional.empty();
    }

    @ShellMethod(key = "create-rule", value = "Create a new rule", group = "Components")
    public String createRule(){
        Optional<RuleConfig> newRule = newRuleDialogCustom(true);
        if(newRule.isPresent()){
            List<RuleConfig> rules = client.getRules();
            rules.add(0, newRule.get());
            client.commitRules(rules);
            return "New rule committed";

        }

        return "Rule creation not confirmed, exiting.";
    }

    @ShellMethod(key="Interactive")
    public String interactive(){
        String[] options = {LIST_APPLICATION_QUERIES, LIST_TABLES, CREATE_RULE, LIST_RULES, EXIT};
        Map<String, String> subCommands = new HashMap<>();
        for(String o : options){
            subCommands.put(o,o);
        }

        String nextAction = "";

        while(!nextAction.equals(EXIT)){
            ComponentFlow flow = componentFlowBuilder.clone().reset()
                    .resourceLoader(getResourceLoader()).templateExecutor(getTemplateExecutor())
                    .withSingleItemSelector("Main Menu")
                    .selectItems(subCommands)
                    .name("Select action")
                    .next(x->null)
                    .and()
                    .build();


            nextAction = (String)flow.run().getContext().get("Main Menu");

            switch (nextAction){
                case CREATE_RULE:
                    createRule();
                    break;
                case LIST_APPLICATION_QUERIES:
                    listQueries();
                    break;
                case LIST_TABLES:
                    listTables();
                    break;
                case LIST_RULES:
                    listRules();
                    break;
            }

            getTerminal().puts(InfoCmp.Capability.clear_screen);
        }

        System.exit(0);

        return "Interactive!";
    }

    public RuleConfig editRuleDialog(RuleInfo rule){
        ComponentFlow.Builder builder = componentFlowBuilder.clone().reset();
        switch (rule.ruleType()){
            case Constants.TABLES:
            case Constants.TABLES_ALL:
            case Constants.TABLES_ANY:
                builder.withStringInput("match")
                        .next(ctx->"TTL")
                        .name("Enter a comma-separated list of tables to match against:")
                        .and();
                break;
            case Constants.QUERY_IDS:
                builder.withStringInput("match")
                        .next(ctx->"TTL")
                        .name("Enter a comma-separated list of Query IDs to match against:")
                        .and();
                break;
            case Constants.REGEX:
                builder.withStringInput("match")
                        .next(ctx->"TTL")
                        .name("Enter a regular expression to match against:")
                        .and();
                break;
            default:
                return rule.getRule();
        }

        builder.withStringInput("TTL")
                .name("Enter a TTL in the form of a duration (e.g. 1h, 300s, 5m):")
                .next(x->"Confirm")
                .and();

        ComponentFlow.ComponentFlowResult result = builder.build().run();

        RuleConfig newRule = rule.getRule().clone();
        String match = result.getContext().get("match");
        String ttl = result.getContext().get("TTL");
        newRule.setTtl(Duration.valueOf(ttl));
        switch (rule.ruleType()){
            case Constants.QUERY_IDS:
                newRule.setQueryIds(Arrays.stream(match.split(",")).collect(Collectors.toList()));
                break;
            case Constants.TABLES:
                newRule.setTables(Arrays.stream(match.split(",")).collect(Collectors.toList()));
                break;
            case Constants.TABLES_ALL:
                newRule.setTablesAll(Arrays.stream(match.split(",")).collect(Collectors.toList()));
                break;
            case Constants.TABLES_ANY:
                newRule.setTablesAny(Arrays.stream(match.split(",")).collect(Collectors.toList()));
                break;
            case Constants.REGEX:
                newRule.setRegex(match);
                break;
        }

        rule.setStatus(RuleInfo.Status.Editing);

        return newRule;
    }

    @ShellMethod(key="list-rules", value = "Display currently active rules in a tabular view")
    public String listRules(){
        List<RuleInfo> rules = client.getRules().stream().map(x->new RuleInfo(x, RuleInfo.Status.Current)).collect(Collectors.toList());


        while (true){
            getTerminal().puts(InfoCmp.Capability.clear_screen);

            List<SelectorItem<RuleInfo>> ruleInfos = rules.stream().map(rule -> SelectorItem.of(UUID.randomUUID().toString(),rule)).collect(Collectors.toList());

            TableSelector<RuleInfo, SelectorItem<RuleInfo>> component = new TableSelector<RuleInfo, SelectorItem<RuleInfo>>(getTerminal(),
                    ruleInfos, "rules", null, RuleInfo.getHeaderRow((getTerminal().getWidth()-10)/4), true, 4, tableInstructions);
            component.setResourceLoader(getResourceLoader());
            component.setTemplateExecutor(getTemplateExecutor());
            TableSelector.SingleItemSelectorContext<RuleInfo, SelectorItem<RuleInfo>> context = component
                    .run(TableSelector.SingleItemSelectorContext.empty(3, tableInstructions));
            Optional<SelectorItem<RuleInfo>> res = context.getResultItem();

            if(component.isConfirmMode()){
                ComponentFlow flow = componentFlowBuilder.clone().reset()
                        .withConfirmationInput("Confirm")
                        .name("Would you like to commit this new configuration?")
                        .next(null)
                        .template("classpath:confirmation-input.stg")
                        .and()
                    .build();
                boolean confirmed = flow.run().getContext().get("Confirm");
                if(confirmed){
                    List<RuleConfig> rulesToCommit = rules.stream().filter(rule->rule.getStatus() != RuleInfo.Status.Delete).map(RuleInfo::getRule).collect(Collectors.toList());
                    client.commitRules(rulesToCommit);
                    rules = client.getRules().stream().map(x->new RuleInfo(x, RuleInfo.Status.Current)).collect(Collectors.toList());
                }

                component.setConfirmMode(false);
                continue;
            }
            if(!component.isEscapeMode() && res.isPresent()){
                res.get().getItem().setRule(editRuleDialog(res.get().getItem()));
            }
            else if(component.isNewMode()){
                Optional<RuleConfig> newRule = newRuleDialog(false);
                if (newRule.isPresent()){
                    rules.add(0, new RuleInfo(newRule.get(), RuleInfo.Status.New));
                }
                component.setNewMode(false);
            }
            else if(component.isDeleteMode()){
                int rowNum = context.getCursorRow();
                RuleInfo rule = context.getItems().get(rowNum).getItem();
                if(rule.getStatus() == RuleInfo.Status.New){
                    rules.remove(rowNum);
                }
                else {
                    rules.get(rowNum).setStatus(RuleInfo.Status.Delete);
                }

                component.setDeleteMode(false);
            }
            else{
                break;
            }

        }
        return "";
    }

    @ShellMethod(key = "list-tables", value = "Get the tables that are currently being watched by smart cache and set up rules for them", group = "Components")
    public String listTables(){
        while(true){
            getTerminal().puts(InfoCmp.Capability.clear_screen);
            List<SelectorItem<Table>> tables = new ArrayList<>();
            for(Table table : client.getTables()){
                tables.add(SelectorItem.of(table.getName(),table));
            }

            TableSelector<Table, SelectorItem<Table>> component = new TableSelector<Table, SelectorItem<Table>>(getTerminal(),
                    tables, "tables", null, Table.headerRow((getTerminal().getWidth()-10)/4), true, 4, tableInstructions);
            component.setResourceLoader(getResourceLoader());
            component.setTemplateExecutor(getTemplateExecutor());
            TableSelector.SingleItemSelectorContext<Table, SelectorItem<Table>> context = component
                    .run(TableSelector.SingleItemSelectorContext.empty(4, tableInstructions));
            Optional<SelectorItem<Table>> res = context.getResultItem();

            if(component.isConfirmMode()){
                component.setConfirmMode(false);
                continue;
            }
            if(!component.isEscapeMode() && res.isPresent()){
                ComponentFlow flow = componentFlowBuilder.clone().reset()
                    .withStringInput("ttl")
                        .name(String.format("Create rule to cache table:%s%nEnter a TTL in the form of a duration (e.g. 1h, 300s, 5m):", res.get().getName()))
                        .next(ctx->"Confirm")
                        .and()
                    .withConfirmationInput("Confirm")
                        .name("Confirm Pending Update")
                        .next(null)
                        .template("classpath:confirmation-input.stg")
                        .and()
                    .build();
                ComponentFlow.ComponentFlowResult flowResult = flow.run();
                boolean confirmed = flowResult.getContext().get("Confirm");
                String ttl = flowResult.getContext().get("ttl");
                if(confirmed && flowResult.getContext().get("ttl") != null){
                    RuleConfig newRule = new RuleConfig.Builder().ttl(Duration.valueOf(ttl)).tablesAny(res.get().getName()).build();
                    List<RuleConfig> rules = client.getRules();
                    rules.add(0, newRule);
                    client.commitRules(rules);
                }
            }
            else{
                break;
            }
        }
        return "";
    }

    private List<SelectorItem<QueryInfo>> getQueries(){
        List<SelectorItem<QueryInfo>> queries = new ArrayList<>();

        for (QueryInfo q : client.getQueries("smartcache")){
            queries.add(SelectorItem.of(q.getQueryId(),q));
        }
        return queries;
    }

    @ShellMethod(key = "list-queries", value = "Get the table of queries", group = "Components")
    public String listQueries(){
        List<RuleConfig> rules = client.getRules();

        Map<Duration, RuleConfig> pendingRules = new HashMap<>();

        List<SelectorItem<QueryInfo>> queries = getQueries();

        while(true){
            getTerminal().puts(InfoCmp.Capability.clear_screen);
            TableSelector<QueryInfo, SelectorItem<QueryInfo>> component = new TableSelector<>(getTerminal(),
                    queries, "queries", null, QueryInfo.getHeaderRow((getTerminal().getWidth()-10)/8), true, 8, tableInstructions);
            component.setResourceLoader(getResourceLoader());
            component.setTemplateExecutor(getTemplateExecutor());
            TableSelector.SingleItemSelectorContext<QueryInfo, SelectorItem<QueryInfo>> context = component
                    .run(TableSelector.SingleItemSelectorContext.empty(8, tableInstructions));
            Optional<SelectorItem<QueryInfo>> resOpt = context.getResultItem();

            if (component.isConfirmMode()){
                Set<String> validResponses = new HashSet<>(Arrays.asList("y","Y","n","N"));
                Optional<Boolean> confirmed = Optional.empty();
                while(!confirmed.isPresent()){

                    String prompt = "Confirm pending updates y/n";
                    StringInput stringInputComponent = new StringInput(getTerminal(), prompt,"n");
                    stringInputComponent.setResourceLoader(getResourceLoader());
                    stringInputComponent.setTemplateExecutor(getTemplateExecutor());
                    StringInput.StringInputContext stringInputContext = stringInputComponent.run(StringInput.StringInputContext.empty());
                    String confirmationInput = stringInputContext.getResultValue();
                    if(validResponses.contains(confirmationInput)){
                        confirmed = Optional.of(confirmationInput.equalsIgnoreCase("y"));
                    }
                    else{
                        continue;
                    }

                    if(confirmed.get()){
                        for(RuleConfig rule : pendingRules.values()){
                            rules.add(0, rule);
                        }

                        client.commitRules(rules);
                        queries = getQueries();
                    }
                    else{
                        component.setConfirmMode(false);
                    }
                }
            }
            else if (!component.isEscapeMode() && resOpt.isPresent()){

                QueryInfo result = resOpt.get().getItem();

                String info = result.toFormattedString(getTerminal().getWidth());
                Optional<Duration> duration = getTtl(info);

                if(!duration.isPresent()){
                    continue;
                }

                RuleConfig rule;
                if(pendingRules.containsKey(duration.get())){
                    pendingRules.get(duration.get()).getQueryIds().add(result.getQueryId());
                    rule = pendingRules.get(duration.get());
                }else{
                    rule = new RuleConfig.Builder().queryIds(result.getQueryId()).ttl(duration.get()).build();
                    pendingRules.put(duration.get(),rule);
                }
                queries.get(context.getCursorRow()).getItem().setPendingRule(rule);
            }
            else{
                break;
            }
        }

        return "";
    }
}

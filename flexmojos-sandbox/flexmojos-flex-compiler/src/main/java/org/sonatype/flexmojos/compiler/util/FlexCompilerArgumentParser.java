package org.sonatype.flexmojos.compiler.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.sonatype.flexmojos.compiler.IFlexArgument;
import org.sonatype.flexmojos.compiler.IFlexConfiguration;
import org.sonatype.flexmojos.generator.iface.StringUtil;

@Component( role = FlexCompilerArgumentParser.class )
public class FlexCompilerArgumentParser
    extends AbstractLogEnabled
{

    public <E> String[] parseArguments( E cfg, Class<? extends E> configClass )
    {
        String[] args = getArgumentsList( cfg, configClass ).toArray( new String[0] );
        if ( getLogger().isDebugEnabled() )
        {
            getLogger().debug( "Compilation arguments:" + toString( args ) );
        }
        return args;
    }

    private CharSequence toString( String[] args )
    {
        StringBuilder sb = new StringBuilder();
        for ( String arg : args )
        {
            if ( arg.startsWith( "-" ) )
            {
                sb.append( '\n' );
            }
            sb.append( arg );
            sb.append( ' ' );
        }
        return sb;
    }

    public <E> List<String> getArgumentsList( E cfg, Class<? extends E> configClass )
    {
        List<Entry<String, List<String>>> charArgs;
        try
        {
            charArgs = doGetArgs( cfg, configClass );
        }
        catch ( Exception e )
        {
            throw new RuntimeException( e );
        }

        List<String> args = new ArrayList<String>();
        for ( Entry<String, List<String>> arg : charArgs )
        {
            args.add( "-" + arg.getName() );
            if ( arg.getValue() != null )
            {
                args.addAll( arg.getValue() );
            }
        }
        return args;
    }

    private <E> List<Entry<String, List<String>>> doGetArgs( E cfg, Class<? extends E> configClass )
        throws Exception
    {
        if ( cfg == null )
        {
            return Collections.emptyList();
        }

        List<Entry<String, List<String>>> args = new LinkedList<Entry<String, List<String>>>();

        Method[] methods = configClass.getDeclaredMethods();
        for ( Method method : methods )
        {
            if ( method.getParameterTypes().length != 0 || !Modifier.isPublic( method.getModifiers() ) )
            {
                continue;
            }

            Object value = method.invoke( cfg );

            if ( value == null )
            {
                continue;
            }

            Class<?> returnType = method.getReturnType();

            if ( value instanceof IFlexConfiguration )
            {
                List<Entry<String, List<String>>> subArgs = doGetArgs( value, returnType );
                String configurationName = parseConfigurationName( method.getName() );
                for ( Entry<String, List<String>> arg : subArgs )
                {
                    args.add( new Entry<String, List<String>>( configurationName + "." + arg.getName(), arg.getValue() ) );
                }
            }
            else if ( value instanceof IFlexArgument || value instanceof IFlexArgument[] )
            {
                IFlexArgument[] values;
                Class<?> type = returnType;
                if ( type.isArray() )
                {
                    values = (IFlexArgument[]) value;
                    type = returnType.getComponentType();
                }
                else
                {
                    values = new IFlexArgument[] { (IFlexArgument) value };
                    type = returnType;
                }

                for ( IFlexArgument iFlexArgument : values )
                {
                    String[] order = (String[]) type.getField( "ORDER" ).get( iFlexArgument );
                    List<String> subArg = new LinkedList<String>();
                    for ( String argMethodName : order )
                    {
                        Object argValue = type.getDeclaredMethod( argMethodName ).invoke( iFlexArgument );
                        if ( argValue == null )
                        {
                            continue;
                        }
                        else if ( argValue instanceof Collection<?> || argValue.getClass().isArray() )
                        {
                            Collection<?> argValues;
                            if ( argValue.getClass().isArray() )
                            {
                                argValues = Arrays.asList( (Object[]) argValue );
                            }
                            else
                            {
                                argValues = (Collection<?>) argValue;
                            }
                            for ( Iterator<?> iterator = argValues.iterator(); iterator.hasNext(); )
                            {
                                subArg.add( iterator.next().toString() );
                            }
                        }
                        else if ( argValue instanceof Map<?, ?> )
                        {
                            Map<?, ?> map = ( (Map<?, ?>) argValue );
                            Set<?> argValues = map.entrySet();
                            for ( Iterator<?> iterator = argValues.iterator(); iterator.hasNext(); )
                            {
                                java.util.Map.Entry<?, ?> entry = (java.util.Map.Entry<?, ?>) iterator.next();
                                subArg.add( entry.getKey().toString() );
                                if ( entry.getValue() != null )
                                {
                                    subArg.add( entry.getValue().toString() );
                                }
                            }

                        }
                        else
                        {
                            subArg.add( argValue.toString() );
                        }
                    }

                    args.add( new Entry<String, List<String>>( parseName( method.getName() ), subArg ) );
                }
            }
            else if ( returnType.isArray() || value instanceof Collection<?> )
            {
                Object[] values;
                if ( returnType.isArray() )
                {
                    values = (Object[]) value;
                }
                else
                {
                    values = ( (Collection<?>) value ).toArray();
                }
                String name = parseName( method.getName() );
                if ( values.length == 0 )
                {
                    args.add( new Entry<String, List<String>>( name + "=", null ) );
                }
                else
                {
                    String appender = "=";
                    for ( Object object : values )
                    {
                        args.add( new Entry<String, List<String>>( name + appender + object.toString(), null ) );
                        appender = "+=";
                    }
                }
            }
            else
            {
                args.add( new Entry<String, List<String>>( parseName( method.getName() ) + "=" + value.toString(), null ) );
            }

        }
        return args;
    }

    private static String parseConfigurationName( String name )
    {
        name = parseName( name );
        name = name.substring( 0, name.length() - 14 );
        return name;
    }

    private static String parseName( String name )
    {
        name = StringUtil.removePrefix( name );
        String[] nodes = StringUtil.splitCamelCase( name );

        StringBuilder finalName = new StringBuilder();
        for ( String node : nodes )
        {
            if ( finalName.length() != 0 )
            {
                finalName.append( '-' );
            }
            finalName.append( node.toLowerCase() );
        }

        return finalName.toString();
    }
}
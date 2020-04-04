#include <stdlib.h>
#include <stdio.h>
#include <time.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>

#include "board.h"

static char solver[3][5] = { "DLX", "CP", "BFS"};


// dup2( pd[0], 0)


static void gosystem( Board b, const char * strategy, int debug){
    char buffer[4000];
    char a1[] = "-d";
    char a2[] = " ";
    char *tmp;

    if( debug ){
        tmp = a1;
    }else{
        tmp = a2;
    }
        
    sprintf(buffer,
        "java pt.ulisboa.tecnico.cnv.solver.SolverMain %s -n1 %d -n2 %d -un %d -i %s -s %s -b %s",
        tmp,
        b.size,
        b.size, 
        b.size*b.size,
        b.name,
        strategy,
        b.board);

    system(buffer);

}

int simple_case(){

    int m = 0;
    float partial, time_spent, dt[3][34];

    int n = 20, child;
    size_t leng = 10;
    int maps = len();
    int pd[2];
    char c, line[200], buf[20];

    FILE* file;

    if (pipe (pd))
    {
        fprintf(stderr, "Pipe failed.\n");
        exit(-1);
    }

    start();

    shuffle(m,1);

    file = fdopen(pd[0], "r");


    for(int pieces = 1; pieces < 37; pieces++){
        shuffle(m,pieces);
        for( int s=0; s<3; s++ )
        {   
            
            time_spent = 0;

            for( int i=0; i< n; i++)
            {
                if(!( child = fork() )){
                    dup2(pd[1], 1);
                    
                    gosystem(get(m), solver[s],1);
                    //printf("\n");
                    exit(0);
                    
                }else{

                    while( wait(NULL) > 0) 
                        ;

                }
                
                while( fgets(line, 200, file)  && !strstr(line, " Solution found in") ) ;

                sscanf(line," Solution found in %f %s",&partial,buf);
                //printf("%s",line);
                time_spent += partial;

            }

            dt[s][pieces] = time_spent/n;
            /*get that info.*/
            //printf("%s \t- ## %s ## \t- %F ms\n", solver[s],get(m).name,dt[s][pieces]);
        }

        //printf("%d \t:: %s - %F : %s - %F : %s - %F \n",pieces,solver[0],dt[0][pieces], solver[1], dt[1][pieces],solver[2], dt[2][pieces]);
    }
    

    //close(pd[0]);
    //close(pd[1]);

    fclose(file);

    return 0;
}

int instructions_count(){

    int m = 0;
    float partial, time_spent;
    int dt[3][36][3];

    int n = 1, child;
    size_t leng = 10;
    int maps = len();
    int pd[2];
    char c, line[200], buf[20];

    FILE* file;

    if (pipe (pd))
    {
        fprintf(stderr, "Pipe failed.\n");
        exit(-1);
    }

    start();

    shuffle(m,1);

    file = fdopen(pd[0], "r");


    for(int pieces = 1; pieces < 37; pieces++){
        shuffle(m,pieces);
        for( int s=0; s<3; s++ )
        {   
            
            time_spent = 0;


                if(!( child = fork() )){
                    dup2(pd[1], 1);
                    
                    gosystem(get(m), solver[s],0);
                    //printf("\n");
                    exit(0);
                    
                }else{

                    while( wait(NULL) > 0) 
                        ;

                }
                
                while( fgets(line, 200, file)  && !strstr(line, " Solution found in") ) ;

                sscanf(line," Solution found in %d-%d-%d",dt[s][pieces-1],dt[s][pieces-1]+1,dt[s][pieces-1]+2);
                //printf("%s",line);

            /*get that info.*/
            //printf("%s \t- ## %s ## \t- %F ms\n", solver[s],get(m).name,dt[s][pieces]);
        }

        //printf("%d \t:: %s - inst %d : bb %d : methods %d :  \n",pieces,solver[0],dt[0][pieces-1][0],dt[0][pieces-1][1],dt[0][pieces-1][2]); 
        //printf("%d \t:: %s - inst %d : bb %d : methods %d :  \n",pieces,solver[1],dt[1][pieces-1][0],dt[1][pieces-1][1],dt[1][pieces-1][2]); 
        //printf("%d \t:: %s - inst %d : bb %d : methods %d :  \n",pieces,solver[2],dt[2][pieces-1][0],dt[2][pieces-1][1],dt[2][pieces-1][2]); 
    
    }

    fclose(file);
    close(pd[0]);
    close(pd[1]);

    printf("data = {\n");
    for(int metric=0; metric < 3; metric++ ){
        printf("\t%d : {\n\t",metric);
        for(int s=0; s < 3; s++){
            printf("\"%s\" ",solver[s]);
            printf(": [");
            for( int i=0; i < 36; i++){
                if( i != 35)
                    printf("%d,",dt[s][i][metric]);
                else
                    printf("%d]",dt[s][i][metric]);

            }
            if( s == 2)
                printf("}");
            else
                printf(",\n\t");
            
        }

        if( metric == 2)
                printf("\n}\n");
            else
                printf(",\n");
    }

    return 0;

}

int stats_case(){

    int m = 0;
    float partial, time_spent;
    int dt[3][36][3];

    int n = 1, child;
    size_t leng = 10;
    int maps = len();
    int pd[2];
    char c, line[200], buf[20];

    FILE* file;

    if (pipe (pd))
    {
        fprintf(stderr, "Pipe failed.\n");
        exit(-1);
    }

    start();

    shuffle(m,1);

    file = fdopen(pd[0], "r");


    for(int pieces = 35; pieces < 37; pieces++){
        shuffle(m,pieces);
        for( int s=0; s<3; s++ )
        {   
            
            time_spent = 0;


                if(!( child = fork() )){
                    dup2(pd[1], 1);
                    
                    gosystem(get(m), solver[s],0);
                    //printf("\n");
                    exit(0);
                    
                }else{

                    while( wait(NULL) > 0) 
                        ;
                }
                
                while( fgets(line, 200, file)  && !strstr(line, "Load Store Summary:") ) 
                    printf("%s",line);

                //sscanf(line," Solution found in %d-%d-%d",dt[s][pieces-1],dt[s][pieces-1]+1,dt[s][pieces-1]+2);
                printf("%s",line);

            /*get that info.*/
            //printf("%s \t- ## %s ## \t- %F ms\n", solver[s],get(m).name,dt[s][pieces]);
        }

        //printf("%d \t:: %s - inst %d : bb %d : methods %d :  \n",pieces,solver[0],dt[0][pieces-1][0],dt[0][pieces-1][1],dt[0][pieces-1][2]); 
        //printf("%d \t:: %s - inst %d : bb %d : methods %d :  \n",pieces,solver[1],dt[1][pieces-1][0],dt[1][pieces-1][1],dt[1][pieces-1][2]); 
        //printf("%d \t:: %s - inst %d : bb %d : methods %d :  \n",pieces,solver[2],dt[2][pieces-1][0],dt[2][pieces-1][1],dt[2][pieces-1][2]); 
    
    }

    fclose(file);
    close(pd[0]);
    close(pd[1]);

    return 0;

}

int main(){
    stats_case();
    return 0;
}